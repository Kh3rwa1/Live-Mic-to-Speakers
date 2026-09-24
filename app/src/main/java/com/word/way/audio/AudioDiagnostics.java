package com.word.way.audio;

/**
 * Diagnostic snapshot of the live audio pipeline state for QA and physical device validation.
 */
public final class AudioDiagnostics {
    private static volatile AudioDiagnostics current = new AudioDiagnostics(0, 0, 0, 0, 0, 0, "Idle", false, 0f);

    public final int sampleRate;
    public final int nativeRate;
    public final int framesPerBuffer;
    public final int recordBufferBytes;
    public final int trackBufferBytes;
    public final int underrunCount;
    public final String route;
    public final boolean lowLatency;
    public final float userGain;
    public final long timestamp;

    public AudioDiagnostics(int sampleRate, int nativeRate, int framesPerBuffer,
                            int recordBufferBytes, int trackBufferBytes,
                            int underrunCount, String route, boolean lowLatency,
                            float userGain) {
        this.sampleRate = sampleRate;
        this.nativeRate = nativeRate;
        this.framesPerBuffer = framesPerBuffer;
        this.recordBufferBytes = recordBufferBytes;
        this.trackBufferBytes = trackBufferBytes;
        this.underrunCount = underrunCount;
        this.route = route != null ? route : "Unknown";
        this.lowLatency = lowLatency;
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
        return "Audio Pipeline Diagnostics:\n" +
                "• Sample Rate: " + sampleRate + " Hz (Native: " + nativeRate + " Hz)\n" +
                "• Buffer Size: " + framesPerBuffer + " frames (" + recordBufferBytes + " B rec / " + trackBufferBytes + " B play)\n" +
                "• Underruns: " + underrunCount + "\n" +
                "• Active Route: " + route + "\n" +
                "• Low Latency Path: " + (lowLatency ? "Active (API 26+)" : "Legacy") + "\n" +
                "• Monitoring Gain: " + Math.round(userGain * 100) + "%";
    }

    @Override public String toString() {
        return toFormattedString();
    }
}
