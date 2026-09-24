package com.word.way.audio;

import java.util.Locale;

/**
 * Preallocated ring buffer capturing the last 30 seconds of per-block feedback detector metrics.
 * Designed for physical device tuning:
 * - Hot path (logBlock) is strictly lock-free, allocation-free, and performs no I/O or string formatting.
 * - Formats CSV on the UI thread only when the user requests an export.
 * - Debug-only; compiled into debug builds and replaced by a no-op stub in release builds.
 */
public final class DetectorLogBuffer {
    /** 30 seconds at 10 ms per block = 3000 blocks. */
    public static final int CAPACITY = 3000;

    private static final DetectorLogBuffer INSTANCE = new DetectorLogBuffer();

    public static DetectorLogBuffer getInstance() {
        return INSTANCE;
    }

    private volatile boolean loggingEnabled = false;

    // Preallocated ring buffers
    private final long[] timeMs = new long[CAPACITY];
    private final float[] rmsDbfs = new float[CAPACITY];
    private final float[] peakDbfs = new float[CAPACITY];
    private final int[] zcr = new int[CAPACITY];
    private final float[] zcrVar = new float[CAPACITY];
    private final boolean[] loud = new boolean[CAPACITY];
    private final boolean[] tonal = new boolean[CAPACITY];
    private final float[] freqEst = new float[CAPACITY];
    private final float[] freqDev = new float[CAPACITY];
    private final float[] purity = new float[CAPACITY];
    private final int[] score = new int[CAPACITY];

    private int writeHead = 0;
    private int count = 0;

    private DetectorLogBuffer() {
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    public void clear() {
        writeHead = 0;
        count = 0;
    }

    public int getLoggedCount() {
        return count;
    }

    /**
     * Hot path: writes one block into the preallocated ring buffer.
     * Allocation-free, lock-free, no I/O.
     */
    public void logBlock(long time, float rms, float peak, int zeroCrossings, float variance,
                         boolean isLoud, boolean isTonal, float freq, float freqRelDev,
                         float spectralPurity, int detectorScore) {
        if (!loggingEnabled) return;
        int idx = writeHead;
        timeMs[idx] = time;
        rmsDbfs[idx] = rms;
        peakDbfs[idx] = peak;
        zcr[idx] = zeroCrossings;
        zcrVar[idx] = variance;
        loud[idx] = isLoud;
        tonal[idx] = isTonal;
        freqEst[idx] = freq;
        freqDev[idx] = freqRelDev;
        purity[idx] = spectralPurity;
        score[idx] = detectorScore;

        writeHead = (idx + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
    }

    /**
     * Cold path: exports the ring buffer contents as CSV string.
     * Executed on the UI thread when user taps Copy diagnostics.
     */
    public String exportCsv() {
        if (count == 0) return "";
        StringBuilder sb = new StringBuilder(count * 64);
        sb.append("timestamp_ms,rms_dbfs,peak_dbfs,zcr,zcr_var,loud,tonal,freq_hz,stability_pct,purity_pct,score\n");
        int start = (count < CAPACITY) ? 0 : writeHead;
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % CAPACITY;
            sb.append(timeMs[idx]).append(',')
              .append(String.format(Locale.US, "%.1f", rmsDbfs[idx])).append(',')
              .append(String.format(Locale.US, "%.1f", peakDbfs[idx])).append(',')
              .append(zcr[idx]).append(',')
              .append(String.format(Locale.US, "%.2f", zcrVar[idx])).append(',')
              .append(loud[idx] ? 1 : 0).append(',')
              .append(tonal[idx] ? 1 : 0).append(',')
              .append(String.format(Locale.US, "%.1f", freqEst[idx])).append(',')
              .append(String.format(Locale.US, "%.2f", freqDev[idx] * 100f)).append(',')
              .append(String.format(Locale.US, "%.1f", purity[idx] * 100f)).append(',')
              .append(score[idx]).append('\n');
        }
        return sb.toString();
    }
}
