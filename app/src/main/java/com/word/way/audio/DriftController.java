package com.word.way.audio;

/**
 * Pure Java drift controller that detects and corrects clock mismatch between
 * audio input and audio output devices.
 *
 * <p>With blocking {@code AudioTrack.write} into an output buffer capped by
 * {@link OutputBufferTuner}, extra delay cannot build up on the output side.
 * When the physical microphone clock runs faster than the DAC playback clock,
 * unread audio accumulates in the {@code AudioRecord} buffer instead.
 *
 * <p>This controller tracks input backlog (e.g. from {@code AudioRecord.getTimestamp}
 * or total frames read vs. elapsed session time). When input backlog remains continuously
 * above burst + 1 burst margin for more than 1.0 second, a smooth crossfade correction
 * (dropping up to 1 burst, &lt;= 2 ms) drains excess delay without audible clicks or pops.
 *
 * <p>Corrections are rate-limited to at most once per 250 ms, and are suppressed
 * during the start-up gain ramp or after acoustic feedback has latched.
 *
 * <p>The output queue depth is retained as a non-correcting diagnostic metric,
 * with a 1-burst margin added to avoid false alarms from stepwise head updates.
 */
public final class DriftController {

    public static final long SUSTAINED_DRIFT_THRESHOLD_MS = 1000L;
    public static final long MIN_CORRECTION_INTERVAL_MS = 250L;

    private final int framesPerBuffer;
    private final int sampleRate;
    private int targetQueueDepthFrames;
    private final long sessionStartTimeMs;

    private long lastRawHead = 0L;
    private long headWrapCount = 0L;

    private long aboveTargetStartTimeMs = -1L;
    private long lastCorrectionTimeMs = 0L;
    private int totalCorrections = 0;

    private long lastInputBacklog = 0L;
    private long lastOutputQueueDepth = 0L;
    private boolean lastOutputQueueAboveDiagnosticThreshold = false;

    public DriftController(int framesPerBuffer, int sampleRate, int targetQueueDepthFrames) {
        this(framesPerBuffer, sampleRate, targetQueueDepthFrames, 0L);
    }

    public DriftController(int framesPerBuffer, int sampleRate, int targetQueueDepthFrames, long initialTimeMs) {
        this.framesPerBuffer = Math.max(1, framesPerBuffer);
        this.sampleRate = Math.max(1, sampleRate);
        this.targetQueueDepthFrames = Math.max(1, targetQueueDepthFrames);
        this.sessionStartTimeMs = initialTimeMs;
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

    public long getSessionStartTimeMs() {
        return sessionStartTimeMs;
    }

    public int getTotalCorrections() {
        return totalCorrections;
    }

    public long getLastInputBacklog() {
        return lastInputBacklog;
    }

    public long getLastOutputQueueDepth() {
        return lastOutputQueueDepth;
    }

    public boolean isOutputQueueAboveDiagnosticThreshold() {
        return lastOutputQueueAboveDiagnosticThreshold;
    }

    /** Threshold for input backlog corrections: burst + 1 burst of margin. */
    public int getInputBacklogThresholdFrames() {
        return framesPerBuffer * 2;
    }

    /** Diagnostic threshold for output queue: target + 1 burst of margin. */
    public int getOutputDiagnosticThresholdFrames() {
        return targetQueueDepthFrames + framesPerBuffer;
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
     * Estimates expected frames captured based on elapsed time since session start.
     */
    public long computeExpectedFrames(long nowMs) {
        long elapsedMs = Math.max(0L, nowMs - sessionStartTimeMs);
        return (elapsedMs * (long) sampleRate) / 1000L;
    }

    /**
     * Computes input backlog from total frames read and elapsed session time.
     */
    public long computeInputBacklog(long totalFramesRead, long nowMs) {
        long expected = computeExpectedFrames(nowMs);
        return Math.max(0L, expected - totalFramesRead);
    }

    /**
     * Evaluates input backlog and triggers drift correction if sustained for &gt; 1 s.
     *
     * @param inputBacklog backlog of unread frames in the input pipeline
     * @param framesWritten total frames written to AudioTrack so far
     * @param rawPlaybackHead raw 32-bit playback head position from AudioTrack
     * @param nowMs current monotonic time in milliseconds
     * @param isRamping true if start-up gain ramp is active
     * @param feedbackLatched true if acoustic feedback has latched mute
     * @return number of frames to drop (0 if no correction is needed)
     */
    public int evaluate(long inputBacklog, long framesWritten, long rawPlaybackHead, long nowMs,
                        boolean isRamping, boolean feedbackLatched) {
        // Output-side check is kept ONLY as a diagnostic, with 1-burst margin added.
        long unwrappedHead = unwrapHeadPosition(rawPlaybackHead);
        this.lastOutputQueueDepth = Math.max(0L, framesWritten - unwrappedHead);
        this.lastOutputQueueAboveDiagnosticThreshold =
                (lastOutputQueueDepth > (long) targetQueueDepthFrames + framesPerBuffer);

        this.lastInputBacklog = Math.max(0L, inputBacklog);

        // No drops during startup ramp or after feedback has latched.
        if (isRamping || feedbackLatched) {
            aboveTargetStartTimeMs = -1L;
            return 0;
        }

        int threshold = framesPerBuffer * 2; // burst + 1 burst margin
        if (inputBacklog > threshold) {
            if (aboveTargetStartTimeMs < 0L) {
                aboveTargetStartTimeMs = nowMs;
            } else if (nowMs - aboveTargetStartTimeMs >= SUSTAINED_DRIFT_THRESHOLD_MS) {
                if (nowMs - lastCorrectionTimeMs >= MIN_CORRECTION_INTERVAL_MS) {
                    int maxDrop = Math.min(framesPerBuffer, Math.max(1, sampleRate * 2 / 1000)); // <= 1 burst, <= 2 ms
                    int excess = (int) Math.min((long) maxDrop, inputBacklog - threshold);
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

    public int evaluate(long inputBacklog, long framesWritten, long rawPlaybackHead, long nowMs) {
        return evaluate(inputBacklog, framesWritten, rawPlaybackHead, nowMs, false, false);
    }

    public int evaluateWithFramesRead(long totalFramesRead, long expectedMicFrames, long framesWritten,
                                      long rawPlaybackHead, long nowMs, boolean isRamping, boolean feedbackLatched) {
        long backlog = Math.max(0L, expectedMicFrames - totalFramesRead);
        return evaluate(backlog, framesWritten, rawPlaybackHead, nowMs, isRamping, feedbackLatched);
    }

    public int evaluateWithFramesRead(long totalFramesRead, long framesWritten, long rawPlaybackHead, long nowMs,
                                      boolean isRamping, boolean feedbackLatched) {
        long backlog = computeInputBacklog(totalFramesRead, nowMs);
        return evaluate(backlog, framesWritten, rawPlaybackHead, nowMs, isRamping, feedbackLatched);
    }

    public int evaluateWithFramesRead(long totalFramesRead, long framesWritten, long rawPlaybackHead, long nowMs) {
        return evaluateWithFramesRead(totalFramesRead, framesWritten, rawPlaybackHead, nowMs, false, false);
    }

    /**
     * Compatibility overload: evaluates using total frames read (or written) against elapsed time.
     */
    public int evaluate(long framesWritten, long rawPlaybackHead, long nowMs) {
        return evaluateWithFramesRead(framesWritten, framesWritten, rawPlaybackHead, nowMs, false, false);
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
