package com.word.way.audio;

import java.util.Arrays;

/**
 * Pure-Java, in-place PCM gain stage and acoustic feedback detector for live monitoring.
 * Kept free of Android types so the ramp, limiter bounds and feedback detector are unit-testable on the JVM.
 *
 * <p>The processor operates in two strictly separated stages:
 * <ol>
 *   <li><b>Analysis on RAW input:</b> Evaluates raw PCM input samples before user gain, startup
 *       ramp or soft limiting are applied. Feedback is detected using multi-criteria block analysis
 *       over a running 500 ms history window (50 blocks at 10 ms/block):
 *       <ul>
 *         <li><b>Loudness:</b> Block RMS must be &ge; {@link #FEEDBACK_RMS_THRESHOLD} (0.30 FS, -10.5 dBFS)
 *             and block peak must be &ge; {@link #FEEDBACK_PEAK_THRESHOLD} (0.60 FS, -4.4 dBFS).</li>
 *         <li><b>Zero-Crossing Tonality:</b> Zero crossings per block must match the running mean within
 *             {@link #TONAL_ZCR_TOLERANCE} (15%), with running mean &ge; {@link #MIN_TONAL_CROSSINGS} (2.0 crossings/block,
 *             i.e. &ge; 100 Hz to reject DC and sub-rumble) and variance &le; {@link #MAX_TONAL_VARIANCE} (5.0).</li>
 *         <li><b>Pitch Stability:</b> Dominant frequency is estimated per block using sub-sample interpolated
 *             zero-crossing intervals. Relative frequency standard deviation across the history window must be &le;
 *             {@link #MAX_FREQ_REL_DEVIATION} (0.008 = 0.8%, ~14 cents). Acoustic feedback holds a fixed electromechanical
 *             room resonance with near-zero drift (< 0.3%), whereas vocal vibrato (&plusmn;30-60 cents, relative std dev > 1.8%)
 *             and speech pitch modulation drift significantly.</li>
 *         <li><b>Spectral Purity:</b> Evaluated via a second-order Goertzel resonator tuned to the dominant frequency.
 *             The concentrated energy ratio must be &ge; {@link #FEEDBACK_MIN_PURITY} (0.80 = 80%). Pure acoustic feedback
 *             concentrates &ge; 95% of its energy into a single resonant sinusoid, whereas voiced singing and held vowels
 *             distribute substantial energy across vocal tract formants and harmonics (spectral purity &le; 0.75).</li>
 *         <li><b>Leaky Score:</b> Blocks meeting all four criteria increment score by {@link #SCORE_INCREMENT} (+2);
 *             other blocks decrement score by {@link #SCORE_DECREMENT} (-1). Mute triggers at {@link #TRIGGER_SCORE} (200,
 *             equivalent to ~1.0 s of continuous feedback) with ceiling {@link #SCORE_MAX} (250).</li>
 *       </ul>
 *   </li>
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

    /**
     * Minimum spectral purity (energy ratio at dominant frequency) to classify a block as feedback.
     * Pure sinusoids mathematically have purity >= 0.95 (up to 1.0), while voiced speech and sung
     * vowels with formant harmonics have purity <= 0.75, and noise has purity <= 0.05.
     */
    static final float FEEDBACK_MIN_PURITY = 0.80f;

    /**
     * Maximum relative frequency standard deviation across the history window (< 0.8%, ~14 cents).
     * Feedback resonance maintains near-zero pitch deviation (< 0.3%), whereas vocal vibrato drifts
     * by 30-60 cents (relative std dev > 1.8%) and speech pitch drifts continuously.
     */
    static final float MAX_FREQ_REL_DEVIATION = 0.008f;

    /** Leaky score increment when a block is both loud and tonal. */
    static final int SCORE_INCREMENT = 2;

    /** Leaky score decrement when a block is quiet or non-tonal. */
    static final int SCORE_DECREMENT = 1;

    /** Score threshold required to trigger feedback mute. At 10 ms/block, 200 corresponds to ~1.0 s sustained tone. */
    public static final int TRIGGER_SCORE = 200;

    /** Maximum clamped score ceiling to prevent unbounded score accumulation. */
    static final int SCORE_MAX = 250;

    /** Number of blocks in the tonality history ring buffer (50 blocks = 500 ms at 10 ms/block). */
    static final int TONAL_WINDOW_BLOCKS = 50;

    private final int sampleRate;
    private final int rampLength;
    private final int blockLength;
    private final short[] blockBuffer;

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
    private float firstCrossingPos = -1f;
    private float lastCrossingPos = -1f;

    // Tonality ring buffer state (preallocated in constructor)
    private final int[] zcrRing = new int[TONAL_WINDOW_BLOCKS];
    private int ringHead;
    private int ringCount;
    private long ringSum;
    private double ringSumSq;

    // Frequency stability ring buffer state (preallocated in constructor)
    private final float[] freqRing = new float[TONAL_WINDOW_BLOCKS];
    private int freqRingHead;
    private int freqRingCount;
    private double freqRingSum;
    private double freqRingSumSq;

    // Leaky detector score
    private int score;

    // Observability state
    private float lastBlockRms;
    private int lastBlockPeak;
    private int lastBlockZcr;
    private float lastBlockZcrVariance;
    private float lastBlockFreqEst;
    private float lastBlockFreqRelDev;
    private float lastBlockPurity;
    private boolean lastBlockLoud;
    private boolean lastBlockTonal;

    public LiveGainProcessor(int sampleRate) {
        this.sampleRate = Math.max(1, sampleRate);
        this.rampLength = Math.max(1, (int) ((long) this.sampleRate * RAMP_MS / 1000L));
        // 10 ms blocks, minimum 32 samples
        this.blockLength = Math.max(32, this.sampleRate / 100);
        this.blockBuffer = new short[this.blockLength];
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

    /** Returns the peak amplitude of the most recently analyzed block. */
    public int getLastBlockPeak() {
        return lastBlockPeak;
    }

    /** Returns the zero-crossing count of the most recently analyzed block. */
    public int getLastBlockZcr() {
        return lastBlockZcr;
    }

    /** Returns the zero-crossing variance over the window. */
    public float getLastBlockZcrVariance() {
        return lastBlockZcrVariance;
    }

    /** Returns whether the last block met the loudness criteria. */
    public boolean isLastBlockLoud() {
        return lastBlockLoud;
    }

    /** Returns whether the last block met the feedback tonality criteria. */
    public boolean isLastBlockTonal() {
        return lastBlockTonal;
    }

    /** Returns the estimated dominant frequency (Hz) of the last block. */
    public float getLastBlockFreqEst() {
        return lastBlockFreqEst;
    }

    /** Returns the relative frequency standard deviation over the window. */
    public float getLastBlockFreqRelDev() {
        return lastBlockFreqRelDev;
    }

    /** Returns the spectral purity [0, 1] of the last block. */
    public float getLastBlockPurity() {
        return lastBlockPurity;
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
        firstCrossingPos = -1f;
        lastCrossingPos = -1f;
        ringHead = 0;
        ringCount = 0;
        ringSum = 0;
        ringSumSq = 0;
        Arrays.fill(zcrRing, 0);
        freqRingHead = 0;
        freqRingCount = 0;
        freqRingSum = 0;
        freqRingSumSq = 0;
        Arrays.fill(freqRing, 0f);
        lastBlockRms = 0f;
        lastBlockPeak = 0;
        lastBlockZcr = 0;
        lastBlockZcrVariance = 0f;
        lastBlockFreqEst = 0f;
        lastBlockFreqRelDev = 0f;
        lastBlockPurity = 0f;
        lastBlockLoud = false;
        lastBlockTonal = false;
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
            blockBuffer[blockSampleCount] = s;
            blockSumSquares += (long) s * s;
            int abs = Math.abs((int) s);
            if (abs > blockPeak) blockPeak = abs;
            if ((s >= 0 && lastRawSample < 0) || (s < 0 && lastRawSample >= 0)) {
                blockZeroCrossings++;
                float fraction = (float) -lastRawSample / (float) (s - lastRawSample);
                float crossingPos = (blockSampleCount - 1) + fraction;
                if (blockZeroCrossings == 1) {
                    firstCrossingPos = crossingPos;
                }
                lastCrossingPos = crossingPos;
            }
            lastRawSample = s;
            blockSampleCount++;

            if (blockSampleCount == blockLength) {
                float rms = (float) Math.sqrt((double) blockSumSquares / blockLength);
                lastBlockRms = rms;
                lastBlockPeak = blockPeak;
                lastBlockZcr = blockZeroCrossings;

                boolean loud = (rms >= FEEDBACK_RMS_THRESHOLD) && (blockPeak >= FEEDBACK_PEAK_THRESHOLD);
                lastBlockLoud = loud;

                boolean tonal = false;
                float purity = 0f;
                float freqRelDev = 1f;
                float variance = 0f;
                float estFreq = 0f;

                if (blockZeroCrossings >= 2 && lastCrossingPos > firstCrossingPos) {
                    float halfPeriod = (lastCrossingPos - firstCrossingPos) / (blockZeroCrossings - 1);
                    if (halfPeriod > 0.1f) {
                        estFreq = (float) sampleRate / (2.0f * halfPeriod);
                    }
                }
                lastBlockFreqEst = estFreq;

                if (loud && estFreq >= 50f && estFreq <= (sampleRate / 2f)) {
                    // 1. Zero crossing rate tonality
                    float mean = ringCount == 0 ? (float) blockZeroCrossings : (float) ringSum / ringCount;
                    float tolerance = Math.max(2.0f, mean * TONAL_ZCR_TOLERANCE);
                    variance = ringCount > 1
                            ? (float) Math.max(0.0, (ringSumSq - (double) ringSum * ringSum / ringCount) / ringCount)
                            : 0f;
                    boolean varianceOk = ringCount < 4 || variance <= MAX_TONAL_VARIANCE;
                    boolean zcrTonal = (Math.abs(blockZeroCrossings - mean) <= tolerance)
                            && (mean >= MIN_TONAL_CROSSINGS)
                            && varianceOk;

                    // 2. Frequency stability across window (pitch stability)
                    float meanFreq = freqRingCount == 0 ? estFreq : (float) (freqRingSum / freqRingCount);
                    float freqVar = freqRingCount > 1
                            ? (float) Math.max(0.0, (freqRingSumSq - (freqRingSum * freqRingSum) / freqRingCount) / freqRingCount)
                            : 0f;
                    float freqStdDev = (float) Math.sqrt(freqVar);
                    freqRelDev = meanFreq > 0f ? freqStdDev / meanFreq : 1f;
                    boolean freqStable = freqRingCount < 4 || freqRelDev <= MAX_FREQ_REL_DEVIATION;

                    // 3. Spectral purity (Goertzel resonator at dominant frequency estFreq)
                    float omega = (float) (2.0 * Math.PI * estFreq / sampleRate);
                    float coeff = (float) (2.0 * Math.cos(omega));
                    float s0 = 0f;
                    float s1 = 0f;
                    float s2 = 0f;
                    for (int j = 0; j < blockLength; j++) {
                        s0 = blockBuffer[j] + coeff * s1 - s2;
                        s2 = s1;
                        s1 = s0;
                    }
                    double power = (double) s1 * s1 + (double) s2 * s2 - (double) coeff * s1 * s2;
                    purity = (blockSumSquares > 0)
                            ? (float) Math.min(1.0, (2.0 * power) / (blockLength * (double) blockSumSquares))
                            : 0f;
                    boolean pure = purity >= FEEDBACK_MIN_PURITY;

                    tonal = zcrTonal && freqStable && pure;

                    // Update ZCR ring buffer
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

                    // Update frequency ring buffer
                    if (freqRingCount < TONAL_WINDOW_BLOCKS) {
                        freqRing[freqRingHead] = estFreq;
                        freqRingSum += estFreq;
                        freqRingSumSq += (double) estFreq * estFreq;
                        freqRingHead = (freqRingHead + 1) % TONAL_WINDOW_BLOCKS;
                        freqRingCount++;
                    } else {
                        float oldFreq = freqRing[freqRingHead];
                        freqRing[freqRingHead] = estFreq;
                        freqRingSum += (estFreq - oldFreq);
                        freqRingSumSq += ((double) estFreq * estFreq - (double) oldFreq * oldFreq);
                        freqRingHead = (freqRingHead + 1) % TONAL_WINDOW_BLOCKS;
                    }
                }

                lastBlockTonal = tonal;
                lastBlockPurity = purity;
                lastBlockFreqRelDev = freqRelDev;
                lastBlockZcrVariance = variance;

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
                        freqRingHead = 0;
                        freqRingCount = 0;
                        freqRingSum = 0;
                        freqRingSumSq = 0;
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
                firstCrossingPos = -1f;
                lastCrossingPos = -1f;

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
