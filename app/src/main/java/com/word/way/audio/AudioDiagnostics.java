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
            "Idle", 0f,
            0, -96f, -96f, 0, 0f,
            false, false, 0f, 0f, 0f
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

    // Feedback detector telemetry
    public final int feedbackScore;
    public final float feedbackRmsDbfs;
    public final float feedbackPeakDbfs;
    public final int feedbackZcr;
    public final float feedbackZcrVariance;
    public final boolean feedbackLoud;
    public final boolean feedbackTonal;
    public final float feedbackFreqRelDev;
    public final float feedbackPurity;
    public final float feedbackFreqEst;

    public AudioDiagnostics(boolean featureLowLatency, boolean featureAudioPro,
                            String audioSource, boolean aecEnabled, boolean nsEnabled,
                            String performanceMode, int bufferCapacityFrames,
                            int bufferSizeFrames, int underrunCount,
                            int framesPerRead, int burstFrames,
                            int sampleRate, int nativeRate,
                            float queueDepthMs, float queueDepth10sMs,
                            long sessionUptimeMs, int driftCorrections,
                            String route, float userGain) {
        this(featureLowLatency, featureAudioPro, audioSource, aecEnabled, nsEnabled,
             performanceMode, bufferCapacityFrames, bufferSizeFrames, underrunCount,
             framesPerRead, burstFrames, sampleRate, nativeRate,
             queueDepthMs, queueDepth10sMs, sessionUptimeMs, driftCorrections,
             route, userGain, 0, -96f, -96f, 0, 0f, false, false, 0f, 0f, 0f);
    }

    public AudioDiagnostics(boolean featureLowLatency, boolean featureAudioPro,
                            String audioSource, boolean aecEnabled, boolean nsEnabled,
                            String performanceMode, int bufferCapacityFrames,
                            int bufferSizeFrames, int underrunCount,
                            int framesPerRead, int burstFrames,
                            int sampleRate, int nativeRate,
                            float queueDepthMs, float queueDepth10sMs,
                            long sessionUptimeMs, int driftCorrections,
                            String route, float userGain,
                            int feedbackScore, float feedbackRmsDbfs, float feedbackPeakDbfs,
                            int feedbackZcr, float feedbackZcrVariance,
                            boolean feedbackLoud, boolean feedbackTonal,
                            float feedbackFreqRelDev, float feedbackPurity, float feedbackFreqEst) {
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
        this.feedbackScore = feedbackScore;
        this.feedbackRmsDbfs = feedbackRmsDbfs;
        this.feedbackPeakDbfs = feedbackPeakDbfs;
        this.feedbackZcr = feedbackZcr;
        this.feedbackZcrVariance = feedbackZcrVariance;
        this.feedbackLoud = feedbackLoud;
        this.feedbackTonal = feedbackTonal;
        this.feedbackFreqRelDev = feedbackFreqRelDev;
        this.feedbackPurity = feedbackPurity;
        this.feedbackFreqEst = feedbackFreqEst;
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
                "• Monitoring Gain: " + Math.round(userGain * 100) + "%\n" +
                "• Feedback Detector: score=" + feedbackScore + "/" + LiveGainProcessor.TRIGGER_SCORE +
                ", loud=" + (feedbackLoud ? "YES" : "NO") + ", tonal=" + (feedbackTonal ? "YES" : "NO") + "\n" +
                "• Detector Metrics: RMS=" + String.format(Locale.US, "%.1f dBFS", feedbackRmsDbfs) +
                ", peak=" + String.format(Locale.US, "%.1f dBFS", feedbackPeakDbfs) +
                ", ZCR=" + feedbackZcr + " (var=" + String.format(Locale.US, "%.2f", feedbackZcrVariance) + ")\n" +
                "• Detector Spectral: freq=" + String.format(Locale.US, "%.1f Hz", feedbackFreqEst) +
                ", stability=" + String.format(Locale.US, "%.2f%%", feedbackFreqRelDev * 100f) +
                ", purity=" + String.format(Locale.US, "%.1f%%", feedbackPurity * 100f);
    }

    @Override public String toString() {
        return toFormattedString();
    }
}
