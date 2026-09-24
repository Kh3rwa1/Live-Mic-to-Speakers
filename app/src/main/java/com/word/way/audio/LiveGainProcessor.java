package com.word.way.audio;

import java.util.Arrays;

/**
 * Pure-Java, in-place PCM gain stage and acoustic feedback detector for live monitoring.
 * Kept free of Android types so the ramp, limiter bounds and feedback detector are unit-testable on the JVM.
 *
 * <p>The processor operates in two strictly separated stages:
 * <ol>
 *   <li><b>Analysis on RAW input:</b> Evaluates raw PCM input samples before user gain, startup
 *       ramp or soft limiting are applied. Feedback is detected using block-based RMS, peak, and
 *       zero-crossing rate (ZCR) tonality over a running history window.
 *       Speech exhibits a high crest factor (peaks significantly higher than RMS, with frequent gaps
 *       between syllables and varying pitch), whereas acoustic feedback is a sustained near-sinusoid
 *       with a crest factor near \u221a2 (~1.414), stable zero-crossing rate and no conversational pauses.</li>
 *   <li><b>Output shaping:</b> Applies a smooth startup ramp from silence to user gain, user gain
 *       scaling, and a monotonic soft limiter. If feedback is triggered, the buffer is muted from the
 *       trigger sample onward, and all subsequent buffers are silenced until {@link #reset()}.</li>
 * </ol>
 *
 * <p>The {@link #setUserGain(float)} method updates the volatile gain field and is thread-safe,
 * though in live sessions {@link AndroidAudioSession} coordinates updates on the single audio worker.
 *
 * <p>The hot path in {@link #process(short[], int)} is strictly allocation-free, lock-free, O(n), and
 * performs no I/O or logging.
 */
public final class LiveGainProcessor {
    /** Full-scale magnitude for signed 16-bit PCM. */
    public static final int FULL_SCALE = 32767;

    /** Limiter knee; samples below this magnitude pass through linearly. */
    public static final float LIMIT_KNEE = FULL_SCALE * 0.8f;

    /**
     * Limiter asymptotic ceiling (32767f). Soft limiting smoothly compresses magnitudes above the
     * knee towards this ceiling and clamps output to {@code LIMIT_CEILING - 1f} (32766) so output
     * magnitude is strictly below full scale, preventing hard clipping and integer overflow.
     */
    public static final float LIMIT_CEILING = FULL_SCALE;

    /** Start-up ramp duration from silence to user gain in milliseconds. */
    public static final long RAMP_MS = 300L;

    /** Minimum RMS threshold for a block to be considered loud (approx. -10.5 dBFS). */
    static final float FEEDBACK_RMS_THRESHOLD = FULL_SCALE * 0.30f;

    /** Minimum peak threshold for a block to be considered loud. */
    static final float FEEDBACK_PEAK_THRESHOLD = FULL_SCALE * 0.60f;

    /** Relative tolerance around the running mean zero-crossing count to classify a block as tonal. */
    static final float TONAL_ZCR_TOLERANCE = 0.15f;

    /** Minimum average zero crossings per block required to avoid latching on DC or sub-100 Hz hum. */
    static final float MIN_TONAL_CROSSINGS = 2.0f;

    /**
     * Maximum zero-crossing count variance across the window for a block to be considered tonal.
     * Pure sinusoids mathematically have per-block ZCR variance <= 0.25, while broadband noise
     * and shouting have variance >> 20.
     */
    static final float MAX_TONAL_VARIANCE = 5.0f;

    /** Leaky score increment when a block is both loud and tonal. */
    static final int SCORE_INCREMENT = 2;

    /** Leaky score decrement when a block is quiet or non-tonal. */
    static final int SCORE_DECREMENT = 1;

    /** Score threshold required to trigger feedback mute. At 10 ms/block, 200 corresponds to ~1.0 s sustained tone. */
    static final int TRIGGER_SCORE = 200;

    /** Maximum clamped score ceiling to prevent unbounded score accumulation. */
    static final int SCORE_MAX = 250;

    /** Number of blocks in the tonality history ring buffer (50 blocks = 500 ms at 10 ms/block). */
    static final int TONAL_WINDOW_BLOCKS = 50;

    private final int sampleRate;
    private final int rampLength;
    private final int blockLength;

    // Output shaping state
    private volatile float userGain = 1f;
    private int rampPosition;
    private boolean muted;
    private boolean feedbackLatched;

    // Block accumulation state (spans process() buffer boundaries)
    private long blockSumSquares;
    private int blockPeak;
    private int blockZeroCrossings;
    private int blockSampleCount;
    private short lastRawSample;

    // Tonality ring buffer state (preallocated in constructor)
    private final int[] zcrRing = new int[TONAL_WINDOW_BLOCKS];
    private int ringHead;
    private int ringCount;
    private long ringSum;
    private double ringSumSq;

    // Leaky detector score
    private int score;

    // Debug observability state
    private float lastBlockRms;
    private int lastBlockZcr;

    public LiveGainProcessor(int sampleRate) {
        this.sampleRate = Math.max(1, sampleRate);
        this.rampLength = Math.max(1, (int) ((long) this.sampleRate * RAMP_MS / 1000L));
        // 10 ms blocks, minimum 32 samples
        this.blockLength = Math.max(32, this.sampleRate / 100);
    }

    /** User gain in [0, 1]; values outside the range are clamped so output can never be boosted. */
    public void setUserGain(float gain) {
        this.userGain = Math.max(0f, Math.min(1f, gain));
    }

    public float getUserGain() {
        return userGain;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isFeedbackLatched() {
        return feedbackLatched;
    }

    /** Returns the current feedback detection score [0, SCORE_MAX]. */
    public int getFeedbackScore() {
        return score;
    }

    /** Returns the RMS of the most recently analyzed block. */
    public float getLastBlockRms() {
        return lastBlockRms;
    }

    /** Returns the zero-crossing count of the most recently analyzed block. */
    public int getLastBlockZcr() {
        return lastBlockZcr;
    }

    /** Clears the ramp position, detector score, accumulators, and latch/mute state. */
    public void reset() {
        rampPosition = 0;
        muted = false;
        feedbackLatched = false;
        score = 0;
        blockSumSquares = 0;
        blockPeak = 0;
        blockZeroCrossings = 0;
        blockSampleCount = 0;
        lastRawSample = 0;
        ringHead = 0;
        ringCount = 0;
        ringSum = 0;
        ringSumSq = 0;
        Arrays.fill(zcrRing, 0);
        lastBlockRms = 0f;
        lastBlockZcr = 0;
    }

    /**
     * Analyzes raw audio for feedback and shapes output in place.
     *
     * @param data  16-bit PCM samples to analyze and shape
     * @param count number of valid samples in {@code data}
     * @return {@code true} exactly once on the buffer where feedback is first triggered;
     *         subsequent buffers are muted and return {@code false} until {@link #reset()}.
     */
    public boolean process(short[] data, int count) {
        if (data == null || count <= 0) return false;
        if (feedbackLatched) {
            blank(data, count);
            return false;
        }

        boolean triggered = false;
        int triggerSampleIndex = -1;

        // Stage 1: Analysis on RAW input samples (pre-gain, pre-ramp, pre-limiter)
        for (int i = 0; i < count; i++) {
            short s = data[i];
            blockSumSquares += (long) s * s;
            int abs = Math.abs((int) s);
            if (abs > blockPeak) blockPeak = abs;
            if ((s >= 0 && lastRawSample < 0) || (s < 0 && lastRawSample >= 0)) {
                blockZeroCrossings++;
            }
            lastRawSample = s;
            blockSampleCount++;

            if (blockSampleCount == blockLength) {
                float rms = (float) Math.sqrt((double) blockSumSquares / blockLength);
                lastBlockRms = rms;
                lastBlockZcr = blockZeroCrossings;

                boolean loud = (rms >= FEEDBACK_RMS_THRESHOLD) && (blockPeak >= FEEDBACK_PEAK_THRESHOLD);
                boolean tonal = false;

                if (loud) {
                    float mean = ringCount == 0 ? (float) blockZeroCrossings : (float) ringSum / ringCount;
                    float tolerance = Math.max(2.0f, mean * TONAL_ZCR_TOLERANCE);
                    float variance = ringCount > 1
                            ? (float) Math.max(0.0, (ringSumSq - (double) ringSum * ringSum / ringCount) / ringCount)
                            : 0f;
                    boolean varianceOk = ringCount < 4 || variance <= MAX_TONAL_VARIANCE;
                    tonal = (Math.abs(blockZeroCrossings - mean) <= tolerance)
                            && (mean >= MIN_TONAL_CROSSINGS)
                            && varianceOk;

                    // Add to tonality history ring buffer
                    if (ringCount < TONAL_WINDOW_BLOCKS) {
                        zcrRing[ringHead] = blockZeroCrossings;
                        ringSum += blockZeroCrossings;
                        ringSumSq += (double) blockZeroCrossings * blockZeroCrossings;
                        ringHead = (ringHead + 1) % TONAL_WINDOW_BLOCKS;
                        ringCount++;
                    } else {
                        int oldVal = zcrRing[ringHead];
                        zcrRing[ringHead] = blockZeroCrossings;
                        ringSum += (blockZeroCrossings - oldVal);
                        ringSumSq += ((double) blockZeroCrossings * blockZeroCrossings - (double) oldVal * oldVal);
                        ringHead = (ringHead + 1) % TONAL_WINDOW_BLOCKS;
                    }
                }

                if (loud && tonal) {
                    score += SCORE_INCREMENT;
                    if (score > SCORE_MAX) score = SCORE_MAX;
                } else {
                    score -= SCORE_DECREMENT;
                    if (score <= 0) {
                        score = 0;
                        ringHead = 0;
                        ringCount = 0;
                        ringSum = 0;
                        ringSumSq = 0;
                    }
                }

                if (score >= TRIGGER_SCORE && !feedbackLatched) {
                    feedbackLatched = true;
                    muted = true;
                    triggered = true;
                    triggerSampleIndex = i;
                }

                // Reset block accumulators for next block
                blockSumSquares = 0;
                blockPeak = 0;
                blockZeroCrossings = 0;
                blockSampleCount = 0;

                if (triggered) {
                    break;
                }
            }
        }

        // Stage 2: Output shaping (ramp -> user gain -> soft limiter)
        if (triggered) {
            for (int i = 0; i <= triggerSampleIndex; i++) {
                float ramp = rampPosition >= rampLength ? 1f : (float) rampPosition / rampLength;
                if (rampPosition < rampLength) rampPosition++;
                data[i] = softLimit(data[i] * userGain * ramp);
            }
            for (int i = triggerSampleIndex + 1; i < count; i++) {
                data[i] = 0;
            }
            return true;
        } else {
            for (int i = 0; i < count; i++) {
                float ramp = rampPosition >= rampLength ? 1f : (float) rampPosition / rampLength;
                if (rampPosition < rampLength) rampPosition++;
                data[i] = softLimit(data[i] * userGain * ramp);
            }
            return false;
        }
    }

    /** Monotonic soft limiter: linear below the knee, asymptotic to the ceiling above it. */
    static short softLimit(float sample) {
        float magnitude = Math.abs(sample);
        if (magnitude <= LIMIT_KNEE) {
            return (short) sample;
        }
        float span = LIMIT_CEILING - LIMIT_KNEE;
        float compressed = LIMIT_KNEE + span * (1f - (float) Math.exp(-(magnitude - LIMIT_KNEE) / span));
        if (compressed >= LIMIT_CEILING) compressed = LIMIT_CEILING - 1f;
        return (short) (sample < 0 ? -compressed : compressed);
    }

    private static void blank(short[] data, int count) {
        Arrays.fill(data, 0, count, (short) 0);
    }
}
