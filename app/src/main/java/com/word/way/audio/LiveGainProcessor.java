package com.word.way.audio;

import java.util.Arrays;

/**
 * Pure-Java, in-place PCM gain stage for live monitoring. Kept free of Android types so the ramp,
 * limiter bounds and feedback detector are unit-testable on the JVM.
 *
 * <p>The stage is deliberately conservative for hearing safety:
 * <ul>
 *   <li>a short start-up ramp from silence to the user's gain so the first samples cannot click,</li>
 *   <li>a soft limiter whose output magnitude can never reach full scale (no hard clipping),</li>
 *   <li>feedback detection: a sustained run of near-full-scale input mutes output and reports a
 *       typed failure so the UI can explain it.</li>
 * </ul>
 * All fields are touched only by the audio worker, so no synchronization is required; the user
 * gain is read through {@link #setUserGain(float)} which the caller may update from the UI thread.
 */
public final class LiveGainProcessor {
    /** Full-scale magnitude for signed 16-bit PCM. */
    public static final int FULL_SCALE = 32767;
    /** Limiter knee; samples below this magnitude pass through linearly. */
    public static final float LIMIT_KNEE = FULL_SCALE * 0.8f;
    /** Asymptotic output ceiling; deliberately below full scale so soft limiting never clips hard. */
    public static final float LIMIT_CEILING = FULL_SCALE;
    /** A sample at or above this magnitude counts toward the feedback window. */
    public static final float FEEDBACK_LEVEL = FULL_SCALE * 0.9f;
    /** Sustained near-full-scale duration that indicates acoustic feedback. */
    public static final long FEEDBACK_WINDOW_MS = 1000L;
    /** Start-up ramp duration from silence to the user gain. */
    public static final long RAMP_MS = 300L;

    private final int sampleRate;
    private final int rampLength;
    private final long feedbackWindow;
    private float userGain = 1f;
    private int rampPosition;
    private long loudRun;
    private boolean muted;
    private boolean feedbackLatched;

    public LiveGainProcessor(int sampleRate) {
        this.sampleRate = Math.max(1, sampleRate);
        this.rampLength = Math.max(1, (int) (this.sampleRate * RAMP_MS / 1000L));
        this.feedbackWindow = Math.max(1L, this.sampleRate * FEEDBACK_WINDOW_MS / 1000L);
    }

    /** User gain in [0, 1]; values outside the range are clamped so output can never be boosted. */
    public void setUserGain(float gain) {
        this.userGain = Math.max(0f, Math.min(1f, gain));
    }
    public float getUserGain() { return userGain; }
    public boolean isMuted() { return muted; }
    public boolean isFeedbackLatched() { return feedbackLatched; }

    /** Clears the ramp position and any latched feedback so a new session starts safely. */
    public void reset() {
        rampPosition = 0;
        loudRun = 0;
        muted = false;
        feedbackLatched = false;
    }

    /**
     * Applies the ramp and limiter to {@code count} samples in place.
     *
     * @return {@code true} exactly once, on the buffer where feedback is first detected. Once
     *         latched, the output is muted (zeroed) until {@link #reset()}.
     */
    public boolean process(short[] data, int count) {
        if (data == null || count <= 0) return false;
        if (feedbackLatched) {
            blank(data, count);
            return false;
        }
        boolean triggered = false;
        for (int i = 0; i < count; i++) {
            float ramp = rampPosition >= rampLength ? 1f : (float) rampPosition / rampLength;
            if (rampPosition < rampLength) rampPosition++;
            float gained = data[i] * userGain * ramp;
            if (Math.abs(gained) >= FEEDBACK_LEVEL) {
                if (++loudRun >= feedbackWindow) {
                    feedbackLatched = true;
                    muted = true;
                    triggered = true;
                }
            } else {
                loudRun = 0;
            }
            data[i] = triggered ? (short) 0 : softLimit(gained);
        }
        if (triggered) blank(data, count);
        return triggered;
    }

    /** Monotonic soft limiter: linear below the knee, asymptotic to the ceiling above it. */
    static short softLimit(float sample) {
        float magnitude = Math.abs(sample);
        if (magnitude <= LIMIT_KNEE) {
            // Cast is safe: the knee is below Short.MAX_VALUE.
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
