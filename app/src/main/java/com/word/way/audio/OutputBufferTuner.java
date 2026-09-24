package com.word.way.audio;

/**
 * Pure Java buffer tuner for {@link android.media.AudioTrack}.
 *
 * <p>Initializes the active playback buffer to a target low-latency size
 * (typically 2 bursts) and dynamically expands the buffer by 1 burst when
 * playback underruns are detected, up to a specified maximum ceiling (typically
 * 4 bursts, or the track's buffer capacity, whichever is smaller).
 *
 * <p>Never shrinks below the minimum burst target during a session to prevent
 * cyclical underruns.
 */
public final class OutputBufferTuner {

    public static final int DEFAULT_MIN_BURST_MULTIPLIER = 2;
    public static final int DEFAULT_MAX_BURST_MULTIPLIER = 4;
    public static final long DEFAULT_COOLDOWN_MS = 250L;

    private final int burstFrames;
    private final int capacityFrames;
    private final int minFrames;
    private final int maxFrames;
    private final long cooldownMs;

    private int currentTargetFrames;
    private int underrunsAtLastAdaptation;
    private long lastAdaptationTimeMs;

    public OutputBufferTuner(int burstFrames, int capacityFrames) {
        this(burstFrames, capacityFrames, DEFAULT_MIN_BURST_MULTIPLIER, DEFAULT_MAX_BURST_MULTIPLIER, DEFAULT_COOLDOWN_MS, 0, 0L);
    }

    public OutputBufferTuner(int burstFrames, int capacityFrames, int initialUnderruns, long initialTimeMs) {
        this(burstFrames, capacityFrames, DEFAULT_MIN_BURST_MULTIPLIER, DEFAULT_MAX_BURST_MULTIPLIER, DEFAULT_COOLDOWN_MS, initialUnderruns, initialTimeMs);
    }

    public OutputBufferTuner(int burstFrames, int capacityFrames,
                             int minMultiplier, int maxMultiplier,
                             long cooldownMs,
                             int initialUnderruns, long initialTimeMs) {
        this.burstFrames = Math.max(1, burstFrames);
        this.capacityFrames = Math.max(this.burstFrames, capacityFrames);
        int validMinMult = Math.max(1, minMultiplier);
        int validMaxMult = Math.max(validMinMult, maxMultiplier);
        this.minFrames = Math.min(this.burstFrames * validMinMult, this.capacityFrames);
        this.maxFrames = Math.max(this.minFrames, Math.min(this.burstFrames * validMaxMult, this.capacityFrames));
        this.cooldownMs = Math.max(0L, cooldownMs);
        this.currentTargetFrames = this.minFrames;
        this.underrunsAtLastAdaptation = Math.max(0, initialUnderruns);
        this.lastAdaptationTimeMs = initialTimeMs;
    }

    public int getBurstFrames() {
        return burstFrames;
    }

    public int getCapacityFrames() {
        return capacityFrames;
    }

    public int getMinFrames() {
        return minFrames;
    }

    public int getMaxFrames() {
        return maxFrames;
    }

    public int getCurrentTargetFrames() {
        return currentTargetFrames;
    }

    public int getUnderrunsAtLastAdaptation() {
        return underrunsAtLastAdaptation;
    }

    public long getLastAdaptationTimeMs() {
        return lastAdaptationTimeMs;
    }

    /**
     * Records the actual size applied by AudioTrack.setBufferSizeInFrames.
     */
    public void recordActualSize(int actualSizeFrames) {
        if (actualSizeFrames > 0) {
            this.currentTargetFrames = Math.max(minFrames, Math.min(maxFrames, actualSizeFrames));
        }
    }

    /**
     * Evaluates current underrun count. If underruns have increased since the last adaptation
     * and cooldown has elapsed, returns the new target buffer size in frames.
     *
     * @param currentUnderruns current total underrun count from AudioTrack
     * @param nowMs current elapsed monotonic time in milliseconds
     * @return the new target buffer size in frames, or -1 if no adaptation was triggered
     */
    public int onUnderrunCheck(int currentUnderruns, long nowMs) {
        if (currentUnderruns < underrunsAtLastAdaptation) {
            // Counter reset or overflow
            underrunsAtLastAdaptation = currentUnderruns;
            return -1;
        }

        if (currentUnderruns > underrunsAtLastAdaptation) {
            if (currentTargetFrames >= maxFrames) {
                // Ceiling reached, cannot grow further
                underrunsAtLastAdaptation = currentUnderruns;
                return -1;
            }

            if (nowMs - lastAdaptationTimeMs >= cooldownMs) {
                underrunsAtLastAdaptation = currentUnderruns;
                lastAdaptationTimeMs = nowMs;
                int newTarget = Math.min(maxFrames, currentTargetFrames + burstFrames);
                if (newTarget > currentTargetFrames) {
                    currentTargetFrames = newTarget;
                    return currentTargetFrames;
                }
            }
        }
        return -1;
    }
}
