package com.word.way.audio;

/**
 * Pure Java drift controller that detects and corrects clock mismatch between
 * audio input and audio output devices.
 *
 * <p>Every hardware device has subtle clock crystal frequency variations. Over time,
 * if the microphone clock runs faster than the DAC playback clock, samples accumulate
 * in the playback queue and latency drifts upward.
 *
 * <p>When queue depth remains continuously above the target depth for more than 1 second,
 * the controller triggers a smooth correction (dropping up to 1 burst, &lt;= 2 ms) with
 * crossfading. Corrections are rate-limited to at most once per 250 ms.
 */
public final class DriftController {

    public static final long SUSTAINED_DRIFT_THRESHOLD_MS = 1000L;
    public static final long MIN_CORRECTION_INTERVAL_MS = 250L;

    private final int framesPerBuffer;
    private final int sampleRate;
    private int targetQueueDepthFrames;

    private long lastRawHead = 0L;
    private long headWrapCount = 0L;

    private long aboveTargetStartTimeMs = -1L;
    private long lastCorrectionTimeMs = 0L;
    private int totalCorrections = 0;

    public DriftController(int framesPerBuffer, int sampleRate, int targetQueueDepthFrames) {
        this(framesPerBuffer, sampleRate, targetQueueDepthFrames, 0L);
    }

    public DriftController(int framesPerBuffer, int sampleRate, int targetQueueDepthFrames, long initialTimeMs) {
        this.framesPerBuffer = Math.max(1, framesPerBuffer);
        this.sampleRate = Math.max(1, sampleRate);
        this.targetQueueDepthFrames = Math.max(1, targetQueueDepthFrames);
        this.lastCorrectionTimeMs = initialTimeMs;
    }

    public int getFramesPerBuffer() {
        return framesPerBuffer;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getTargetQueueDepthFrames() {
        return targetQueueDepthFrames;
    }

    public void setTargetQueueDepthFrames(int target) {
        if (target > 0) {
            this.targetQueueDepthFrames = target;
        }
    }

    public int getTotalCorrections() {
        return totalCorrections;
    }

    /**
     * Unwraps 32-bit unsigned AudioTrack playback head position to 64-bit continuous frames.
     */
    public long unwrapHeadPosition(long rawHead) {
        long unsignedRaw = rawHead & 0xFFFFFFFFL;
        if (unsignedRaw < (lastRawHead & 0xFFFFFFFFL)) {
            headWrapCount++;
        }
        lastRawHead = unsignedRaw;
        return (headWrapCount << 32) | unsignedRaw;
    }

    /**
     * Evaluates queue depth and returns the number of frames to skip if sustained drift
     * has been detected for &gt; 1 second and the 250ms cooldown has elapsed.
     *
     * @param framesWritten total frames written to AudioTrack so far
     * @param rawPlaybackHead raw 32-bit playback head position from AudioTrack
     * @param nowMs current monotonic time in milliseconds
     * @return number of frames to drop (0 if no correction is needed)
     */
    public int evaluate(long framesWritten, long rawPlaybackHead, long nowMs) {
        long unwrappedHead = unwrapHeadPosition(rawPlaybackHead);
        long queueDepth = Math.max(0L, framesWritten - unwrappedHead);

        if (queueDepth > targetQueueDepthFrames) {
            if (aboveTargetStartTimeMs < 0L) {
                aboveTargetStartTimeMs = nowMs;
            } else if (nowMs - aboveTargetStartTimeMs >= SUSTAINED_DRIFT_THRESHOLD_MS) {
                if (nowMs - lastCorrectionTimeMs >= MIN_CORRECTION_INTERVAL_MS) {
                    int maxDrop = Math.min(framesPerBuffer, Math.max(1, sampleRate * 2 / 1000)); // <= 1 burst, <= 2ms
                    int excess = (int) Math.min((long) maxDrop, queueDepth - targetQueueDepthFrames);
                    int dropFrames = Math.max(1, excess);

                    lastCorrectionTimeMs = nowMs;
                    aboveTargetStartTimeMs = -1L;
                    totalCorrections++;
                    return dropFrames;
                }
            }
        } else {
            aboveTargetStartTimeMs = -1L;
        }

        return 0;
    }

    /**
     * Smoothly drops {@code dropFrames} from {@code buffer} in place by applying a short
     * linear crossfade of length {@code crossfadeFrames}, preventing clicks and pops.
     *
     * @param buffer PCM 16-bit audio buffer
     * @param count number of valid samples in {@code buffer}
     * @param dropFrames number of frames to drop
     * @param crossfadeFrames crossfade length in samples (typically &lt;= 2 ms)
     * @return the new valid sample count in {@code buffer}
     */
    public static int applyCrossfadeDrop(short[] buffer, int count, int dropFrames, int crossfadeFrames) {
        if (buffer == null || count <= 0) return 0;
        if (dropFrames <= 0) return count;
        int drop = Math.min(dropFrames, count - 1);
        int crossfade = Math.min(crossfadeFrames, count - drop);
        if (crossfade <= 0) {
            int newCount = count - drop;
            System.arraycopy(buffer, drop, buffer, 0, newCount);
            return newCount;
        }

        for (int i = 0; i < crossfade; i++) {
            float alpha = (float) i / crossfade;
            int s1 = buffer[i];
            int s2 = buffer[i + drop];
            buffer[i] = (short) Math.round((1f - alpha) * s1 + alpha * s2);
        }

        int remaining = count - drop - crossfade;
        if (remaining > 0) {
            System.arraycopy(buffer, crossfade + drop, buffer, crossfade, remaining);
        }

        return count - drop;
    }
}
