package com.word.way.audio;

/** Relative digital peak only: never a calibrated sound-pressure or dB meter. */
public final class AudioLevels {
    private AudioLevels() { }
    public static int percent(int amplitude) {
        return (int) (Math.min(32767L, Math.max(0L, amplitude)) * 100L / 32767L);
    }
}
