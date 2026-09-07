package com.example.livemictospeaker.audio;

public final class RecordingRules {
    public static final long MIN_DURATION_MS = 500;
    private RecordingRules() {}
    public static boolean keep(boolean requested, boolean stoppedCleanly, long durationMs, long bytes) {
        return requested && stoppedCleanly && durationMs >= MIN_DURATION_MS && bytes > 0;
    }
}
