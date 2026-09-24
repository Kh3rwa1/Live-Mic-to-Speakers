package com.word.way.audio;

/**
 * Release no-op stub. Preallocated buffers and CSV export are debug-only.
 */
public final class DetectorLogBuffer {
    private static final DetectorLogBuffer INSTANCE = new DetectorLogBuffer();

    public static DetectorLogBuffer getInstance() {
        return INSTANCE;
    }

    private DetectorLogBuffer() {
    }

    public boolean isLoggingEnabled() {
        return false;
    }

    public void setLoggingEnabled(boolean enabled) {
    }

    public void clear() {
    }

    public int getLoggedCount() {
        return 0;
    }

    public void logBlock(long time, float rms, float peak, int zeroCrossings, float variance,
                         boolean isLoud, boolean isTonal, float freq, float freqRelDev,
                         float spectralPurity, int detectorScore) {
    }

    public String exportCsv() {
        return "";
    }
}
