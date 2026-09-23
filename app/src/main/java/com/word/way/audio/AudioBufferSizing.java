package com.word.way.audio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, dependency-free sizing rules for the live-monitoring session. Kept out of the Android
 * class so the native-rate ordering and buffer sizing stay unit-testable on the JVM.
 *
 * <p>Buffers are a small multiple of the device's native output burst so the audio framework can
 * render with low latency, but never below the platform minimum returned by
 * {@code getMinBufferSize}. A too-small buffer is more likely to underrun than to reduce latency.
 */
public final class AudioBufferSizing {
    /** Rates to try after the device's native output rate, mirroring the previous fallback order. */
    public static final int[] FALLBACK_RATES = {48000, 44100, 16000, 8000};
    /** Two bursts gives the platform room to absorb scheduling jitter without over-buffering. */
    public static final int BURST_MULTIPLIER = 2;

    private AudioBufferSizing() { }

    /**
     * Device's native rate first (when known), then the legacy fallbacks, de-duplicated so a native
     * 48 kHz device does not probe 48 kHz twice.
     */
    public static int[] candidateRates(int nativeRate) {
        Set<Integer> rates = new LinkedHashSet<>();
        if (nativeRate > 0) rates.add(nativeRate);
        for (int rate : FALLBACK_RATES) rates.add(rate);
        int[] ordered = new int[rates.size()];
        int index = 0;
        for (int rate : rates) ordered[index++] = rate;
        return ordered;
    }

    /**
     * Chooses a buffer size for one direction: a small multiple of the native burst when that is
     * known, otherwise the platform minimum. Never returns less than {@code minBytes}.
     */
    public static int bufferBytes(int minBytes, int framesPerBuffer, int channelCount, int bytesPerSample) {
        if (minBytes <= 0) return minBytes;
        long burst = 0L;
        if (framesPerBuffer > 0 && channelCount > 0 && bytesPerSample > 0) {
            burst = (long) framesPerBuffer * channelCount * bytesPerSample * BURST_MULTIPLIER;
        }
        return (int) Math.max(minBytes, burst);
    }

    /** Scratch PCM buffer in 16-bit samples that can hold the larger of the two buffers. */
    public static int scratchSamples(int recordBufferBytes, int trackBufferBytes, int bytesPerSample) {
        int bytes = Math.max(recordBufferBytes, trackBufferBytes);
        if (bytes <= 0 || bytesPerSample <= 0) return 0;
        return (bytes + bytesPerSample - 1) / bytesPerSample;
    }

    /** Ordered, human-readable probe list; debug logging only. */
    public static List<Integer> toList(int[] rates) {
        List<Integer> list = new ArrayList<>(rates.length);
        for (int rate : rates) list.add(rate);
        return list;
    }
}
