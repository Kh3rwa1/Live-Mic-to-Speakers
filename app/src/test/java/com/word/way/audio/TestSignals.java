package com.word.way.audio;

import java.util.Random;

/**
 * Deterministic test signals for {@link LiveGainProcessorTest}.
 */
public final class TestSignals {
    private TestSignals() { }

    /** Generates a pure sine wave. */
    public static short[] sine(int sampleRate, double freqHz, double amplitudeFraction, double seconds) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        double twoPiF = 2.0 * Math.PI * freqHz;
        double peak = amplitudeFraction * LiveGainProcessor.FULL_SCALE;
        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double sample = Math.sin(twoPiF * t) * peak;
            data[i] = (short) Math.round(Math.max(-LiveGainProcessor.FULL_SCALE, Math.min(LiveGainProcessor.FULL_SCALE, sample)));
        }
        return data;
    }

    /** Generates silence (all zero samples). */
    public static short[] silence(int sampleRate, double seconds) {
        int length = (int) Math.round(sampleRate * seconds);
        return new short[length];
    }

    /** Generates low-amplitude noise (<= 5% of full scale). */
    public static short[] lowNoise(int sampleRate, double seconds, long seed) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        Random random = new Random(seed);
        int maxAmp = (int) Math.round(0.05 * LiveGainProcessor.FULL_SCALE);
        for (int i = 0; i < length; i++) {
            data[i] = (short) Math.round((random.nextDouble() * 2.0 - 1.0) * maxAmp);
        }
        return data;
    }

    /**
     * Generates continuous broadband noise with no gaps, hard-clipped at 0.95 full scale.
     * Sustained, loud, but non-tonal.
     */
    public static short[] shoutLike(int sampleRate, double seconds, long seed) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        Random random = new Random(seed);
        int clip = (int) Math.round(0.95 * LiveGainProcessor.FULL_SCALE);
        for (int i = 0; i < length; i++) {
            // Amplified uniform noise that hard-clips frequently
            double val = (random.nextDouble() * 2.0 - 1.0) * LiveGainProcessor.FULL_SCALE * 2.0;
            if (val > clip) val = clip;
            else if (val < -clip) val = -clip;
            data[i] = (short) Math.round(val);
        }
        return data;
    }

    /**
     * Band-limited noise amplitude-modulated at a syllable rate (~4 Hz) with random gaps of
     * 80-250 ms making up at least 25% of the total time, peaks scaled to 0.9 full scale.
     */
    public static short[] speechLike(int sampleRate, double seconds, long seed) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        Random random = new Random(seed);

        // First pass: generate band-limited noise using a simple resonant/band-pass filter
        // (one-pole high-pass ~200 Hz, one-pole low-pass ~3500 Hz)
        double hpFreq = 200.0;
        double lpFreq = 3500.0;
        double rcHp = 1.0 / (2.0 * Math.PI * hpFreq);
        double dt = 1.0 / sampleRate;
        double alphaHp = rcHp / (rcHp + dt);

        double rcLp = 1.0 / (2.0 * Math.PI * lpFreq);
        double alphaLp = dt / (rcLp + dt);

        double[] rawNoise = new double[length];
        double prevNoise = 0.0;
        double prevHp = 0.0;
        double prevLp = 0.0;
        for (int i = 0; i < length; i++) {
            double white = random.nextDouble() * 2.0 - 1.0;
            // High-pass filter
            double hp = alphaHp * (prevHp + white - prevNoise);
            prevNoise = white;
            prevHp = hp;
            // Low-pass filter
            double lp = prevLp + alphaLp * (hp - prevLp);
            prevLp = lp;
            rawNoise[i] = lp;
        }

        // Generate syllables with alternating active bursts (100-250 ms) and gaps (80-250 ms)
        // Ensure at least 25% gap time.
        double[] envelope = new double[length];
        int pos = 0;
        while (pos < length) {
            int burstSamples = (int) Math.round(sampleRate * (0.10 + random.nextDouble() * 0.15)); // 100-250 ms
            int gapSamples = (int) Math.round(sampleRate * (0.08 + random.nextDouble() * 0.17));   // 80-250 ms
            int burstEnd = Math.min(length, pos + burstSamples);
            int actualBurst = burstEnd - pos;
            for (int j = 0; j < actualBurst; j++) {
                // Raised cosine envelope for the syllable
                double progress = (double) j / actualBurst;
                envelope[pos + j] = Math.sin(Math.PI * progress);
            }
            pos = burstEnd;
            int gapEnd = Math.min(length, pos + gapSamples);
            // Gaps remain 0.0 in envelope
            pos = gapEnd;
        }

        // Apply envelope and find max peak to scale to 0.9 full scale
        double maxPeak = 0.0;
        double[] modulated = new double[length];
        for (int i = 0; i < length; i++) {
            modulated[i] = rawNoise[i] * envelope[i];
            double abs = Math.abs(modulated[i]);
            if (abs > maxPeak) maxPeak = abs;
        }

        double scale = maxPeak > 0 ? (0.90 * LiveGainProcessor.FULL_SCALE) / maxPeak : 1.0;
        for (int i = 0; i < length; i++) {
            double val = modulated[i] * scale;
            data[i] = (short) Math.round(Math.max(-LiveGainProcessor.FULL_SCALE, Math.min(LiveGainProcessor.FULL_SCALE, val)));
        }
        return data;
    }
}
