package com.word.way.util;

import java.util.Locale;

/**
 * Standard duration formatter for audio recordings and media playback.
 */
public final class TimeFormat {
    private TimeFormat() { }

    /**
     * Formats duration in milliseconds into HH:mm:ss or mm:ss display string.
     * Non-positive values format as "00:00".
     */
    public static String formatDuration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return seconds >= 3600
                ? String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
                : String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
}
