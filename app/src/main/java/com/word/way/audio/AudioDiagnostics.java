package com.word.way.audio;

import java.util.Locale;

/**
 * Diagnostic snapshot of the live audio pipeline state for QA and physical device validation.
 * Snapshots are updated from the audio worker at most every 500 ms without per-buffer allocations.
 */
public final class AudioDiagnostics {
    private static volatile AudioDiagnostics current = new AudioDiagnostics(
            false, false, "Unknown", false, false,
            "Unknown", 0, 0, 0,
            0, 0, 0, 0,
            0f, -1f, 0L, 0,
            "Idle", 0f
    );

    // Platform features
    public final boolean featureLowLatency;
    public final boolean featureAudioPro;

    // Audio input configuration
    public final String audioSource;
    public final boolean aecEnabled;
    public final boolean nsEnabled;

    // Audio output configuration & metrics
    public final String performanceMode;
    public final int bufferCapacityFrames;
    public final int bufferSizeFrames;
    public final int underrunCount;

    // Sizing & rates
    public final int framesPerRead;
    public final int burstFrames;
    public final int sampleRate;
    public final int nativeRate;

    // Queue depth & drift tracking
    public final float queueDepthMs;
    public final float queueDepth10sMs;
    public final long sessionUptimeMs;
    public final int driftCorrections;

    // Session route & gain
    public final String route;
    public final float userGain;
    public final long timestamp;

    public AudioDiagnostics(boolean featureLowLatency, boolean featureAudioPro,
                            String audioSource, boolean aecEnabled, boolean nsEnabled,
                            String performanceMode, int bufferCapacityFrames,
                            int bufferSizeFrames, int underrunCount,
                            int framesPerRead, int burstFrames,
                            int sampleRate, int nativeRate,
                            float queueDepthMs, float queueDepth10sMs,
                            long sessionUptimeMs, int driftCorrections,
                            String route, float userGain) {
        this.featureLowLatency = featureLowLatency;
        this.featureAudioPro = featureAudioPro;
        this.audioSource = audioSource != null ? audioSource : "Unknown";
        this.aecEnabled = aecEnabled;
        this.nsEnabled = nsEnabled;
        this.performanceMode = performanceMode != null ? performanceMode : "Unknown";
        this.bufferCapacityFrames = bufferCapacityFrames;
        this.bufferSizeFrames = bufferSizeFrames;
        this.underrunCount = underrunCount;
        this.framesPerRead = framesPerRead;
        this.burstFrames = burstFrames;
        this.sampleRate = sampleRate;
        this.nativeRate = nativeRate;
        this.queueDepthMs = queueDepthMs;
        this.queueDepth10sMs = queueDepth10sMs;
        this.sessionUptimeMs = sessionUptimeMs;
        this.driftCorrections = driftCorrections;
        this.route = route != null ? route : "Unknown";
        this.userGain = userGain;
        this.timestamp = System.currentTimeMillis();
    }

    public static AudioDiagnostics get() {
        return current;
    }

    public static void update(AudioDiagnostics diagnostics) {
        if (diagnostics != null) {
            current = diagnostics;
        }
    }

    public String toFormattedString() {
        float driftMs = (queueDepth10sMs >= 0f) ? (queueDepthMs - queueDepth10sMs) : 0f;
        String driftText = (queueDepth10sMs >= 0f)
                ? String.format(Locale.US, "%.1f ms (drift: %+.1f ms)", queueDepth10sMs, driftMs)
                : "measuring...";

        return "Audio Pipeline Diagnostics:\n" +
                "• Hardware Flags: LowLatency=" + featureLowLatency + ", ProAudio=" + featureAudioPro + "\n" +
                "• Input Source: " + audioSource + " (AEC=" + (aecEnabled ? "on" : "off") + ", NS=" + (nsEnabled ? "on" : "off") + ")\n" +
                "• AudioTrack: mode=" + performanceMode + ", capacity=" + bufferCapacityFrames + " f, size=" + bufferSizeFrames + " f, underruns=" + underrunCount + "\n" +
                "• Rates & Bursts: " + sampleRate + " Hz (Native: " + nativeRate + " Hz), burst=" + burstFrames + " f, read=" + framesPerRead + " f\n" +
                "• Queue Depth: " + String.format(Locale.US, "%.1f ms", queueDepthMs) + " (at 10s: " + driftText + ")\n" +
                "• Drift Corrections: " + driftCorrections + "\n" +
                "• Uptime: " + String.format(Locale.US, "%.1fs", sessionUptimeMs / 1000f) + "\n" +
                "• Route: " + route + "\n" +
                "• Monitoring Gain: " + Math.round(userGain * 100) + "%";
    }

    @Override public String toString() {
        return toFormattedString();
    }
}
