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

    /** Generates a steady feedback sine (matches existing sine generator). */
    public static short[] steadyFeedback(int sampleRate, double freqHz, double amplitudeFraction, double seconds) {
        return sine(sampleRate, freqHz, amplitudeFraction, seconds);
    }

    /**
     * Generates a growing feedback sine whose amplitude starts at startAmplitude and grows at
     * growthDbPerSecond until saturating at 1.0 full scale.
     */
    public static short[] growingFeedback(int sampleRate, double freqHz, double seconds,
                                          double startAmplitude, double growthDbPerSecond) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        double twoPiF = 2.0 * Math.PI * freqHz;
        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double gainLinear = Math.pow(10.0, (growthDbPerSecond * t) / 20.0);
            double currentAmp = Math.min(1.0, startAmplitude * gainLinear);
            double sample = Math.sin(twoPiF * t) * (currentAmp * LiveGainProcessor.FULL_SCALE);
            data[i] = (short) Math.round(Math.max(-LiveGainProcessor.FULL_SCALE, Math.min(LiveGainProcessor.FULL_SCALE, sample)));
        }
        return data;
    }

    /** Simple formant resonance weighting for human vowel /a/. */
    private static double vowelFormantWeight(double freqHz) {
        // Formants for /a/: F1 = 800 Hz (bw 120), F2 = 1200 Hz (bw 150), F3 = 2500 Hz (bw 220)
        double r1 = 1.0 / Math.sqrt(1.0 + Math.pow((freqHz - 800.0) / 60.0, 2));
        double r2 = 0.7 / Math.sqrt(1.0 + Math.pow((freqHz - 1200.0) / 75.0, 2));
        double r3 = 0.35 / Math.sqrt(1.0 + Math.pow((freqHz - 2500.0) / 110.0, 2));
        return 0.15 + r1 + r2 + r3;
    }

    /**
     * Generates a realistic sung vowel with fundamental plus 8 harmonics (-6 dB/oct tilt shaped by
     * vowel formants), vibrato (vibratoHz, vibratoCents), slight amplitude jitter (+-1.5 dB) and
     * small random pitch jitter (+-6 cents).
     */
    public static short[] sungVowel(int sampleRate, double f0, double seconds, double amplitude,
                                   double vibratoHz, double vibratoCents, long seed) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        Random random = new Random(seed);

        int numHarmonics = 9; // fundamental (h=1) + 8 harmonics
        double[] harmonicWeights = new double[numHarmonics + 1];
        double[] harmonicPhases = new double[numHarmonics + 1];
        for (int h = 1; h <= numHarmonics; h++) {
            double hFreq = f0 * h;
            if (hFreq < sampleRate / 2.0) {
                // -6 dB/octave tilt (1/h) shaped by vowel formants
                harmonicWeights[h] = (1.0 / h) * vowelFormantWeight(hFreq);
                harmonicPhases[h] = random.nextDouble() * 2.0 * Math.PI;
            }
        }

        // Generate slow pitch and amplitude jitter tables (interpolated every ~20 ms)
        int step = Math.max(1, sampleRate / 50); // 20 ms
        int numControlPoints = (length / step) + 2;
        double[] pitchJitterPts = new double[numControlPoints];
        double[] ampJitterPts = new double[numControlPoints];
        for (int k = 0; k < numControlPoints; k++) {
            // Pitch jitter: +-6 cents
            pitchJitterPts[k] = (random.nextDouble() * 2.0 - 1.0) * 6.0;
            // Amp jitter: +-1.5 dB
            ampJitterPts[k] = (random.nextDouble() * 2.0 - 1.0) * 1.5;
        }

        double phase = 0.0;
        double[] raw = new double[length];
        double maxAbs = 0.0;

        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;

            // Interpolate jitter
            int idx = i / step;
            double frac = (double) (i % step) / step;
            double pitchJitter = pitchJitterPts[idx] * (1.0 - frac) + pitchJitterPts[idx + 1] * frac;
            double ampJitterDb = ampJitterPts[idx] * (1.0 - frac) + ampJitterPts[idx + 1] * frac;
            double ampJitterLinear = Math.pow(10.0, ampJitterDb / 20.0);

            // Vibrato + pitch jitter in cents
            double cents = vibratoCents * Math.sin(2.0 * Math.PI * vibratoHz * t) + pitchJitter;
            double instFreq = f0 * Math.pow(2.0, cents / 1200.0);

            phase += (2.0 * Math.PI * instFreq) / sampleRate;
            if (phase >= 2.0 * Math.PI) {
                phase -= 2.0 * Math.PI * Math.floor(phase / (2.0 * Math.PI));
            }

            double sampleSum = 0.0;
            for (int h = 1; h <= numHarmonics; h++) {
                if (harmonicWeights[h] > 0) {
                    sampleSum += harmonicWeights[h] * Math.sin(h * phase + harmonicPhases[h]);
                }
            }
            sampleSum *= ampJitterLinear;
            raw[i] = sampleSum;
            double abs = Math.abs(sampleSum);
            if (abs > maxAbs) maxAbs = abs;
        }

        double scale = maxAbs > 0 ? (amplitude * LiveGainProcessor.FULL_SCALE) / maxAbs : 1.0;
        for (int i = 0; i < length; i++) {
            double val = raw[i] * scale;
            data[i] = (short) Math.round(Math.max(-LiveGainProcessor.FULL_SCALE, Math.min(LiveGainProcessor.FULL_SCALE, val)));
        }
        return data;
    }

    /**
     * Generates a perfectly steady harmonic note with no vibrato or jitter (the hardest case).
     * Fundamental plus 8 harmonics with -6 dB/octave tilt shaped by vowel formants.
     */
    public static short[] heldNoteNoVibrato(int sampleRate, double f0, double seconds, double amplitude) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];

        int numHarmonics = 9;
        double[] harmonicWeights = new double[numHarmonics + 1];
        for (int h = 1; h <= numHarmonics; h++) {
            double hFreq = f0 * h;
            if (hFreq < sampleRate / 2.0) {
                harmonicWeights[h] = (1.0 / h) * vowelFormantWeight(hFreq);
            }
        }

        double[] raw = new double[length];
        double maxAbs = 0.0;
        double twoPiF0 = 2.0 * Math.PI * f0;

        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double sampleSum = 0.0;
            for (int h = 1; h <= numHarmonics; h++) {
                if (harmonicWeights[h] > 0) {
                    sampleSum += harmonicWeights[h] * Math.sin(h * twoPiF0 * t);
                }
            }
            raw[i] = sampleSum;
            double abs = Math.abs(sampleSum);
            if (abs > maxAbs) maxAbs = abs;
        }

        double scale = maxAbs > 0 ? (amplitude * LiveGainProcessor.FULL_SCALE) / maxAbs : 1.0;
        for (int i = 0; i < length; i++) {
            double val = raw[i] * scale;
            data[i] = (short) Math.round(Math.max(-LiveGainProcessor.FULL_SCALE, Math.min(LiveGainProcessor.FULL_SCALE, val)));
        }
        return data;
    }

    /**
     * Generates a sequence of sung notes (0.3-1.5 s each) at different pitches across 110-800 Hz
     * with short gaps (60-150 ms) representing breaths or consonant transitions.
     */
    public static short[] singingPhrase(int sampleRate, double seconds, long seed) {
        int length = (int) Math.round(sampleRate * seconds);
        short[] data = new short[length];
        Random random = new Random(seed);

        // Pentatonic-like scale steps across 110-800 Hz
        double[] pitchChoices = {
            110.0, 130.81, 146.83, 164.81, 196.0, 220.0, 261.63, 293.66,
            329.63, 392.0, 440.0, 523.25, 587.33, 659.25, 783.99
        };

        int pos = 0;
        while (pos < length) {
            double noteDur = 0.3 + random.nextDouble() * 1.2; // 0.3s to 1.5s
            double f0 = pitchChoices[random.nextInt(pitchChoices.length)];
            double vibratoHz = 5.0 + random.nextDouble() * 1.5; // 5.0 - 6.5 Hz
            double vibratoCents = 35.0 + random.nextDouble() * 25.0; // 35 - 60 cents
            long noteSeed = random.nextLong();

            short[] note = sungVowel(sampleRate, f0, noteDur, 0.85, vibratoHz, vibratoCents, noteSeed);
            int copyLen = Math.min(note.length, length - pos);
            System.arraycopy(note, 0, data, pos, copyLen);
            pos += copyLen;

            if (pos >= length) break;

            // Short gap/breath 60-150 ms
            int gapSamples = (int) Math.round(sampleRate * (0.06 + random.nextDouble() * 0.09));
            // Gap remains low-noise breath or silence
            int gapEnd = Math.min(length, pos + gapSamples);
            while (pos < gapEnd) {
                data[pos] = (short) Math.round((random.nextDouble() * 2.0 - 1.0) * 0.02 * LiveGainProcessor.FULL_SCALE);
                pos++;
            }
        }

        // Scale max peak to 0.90 FS
        double maxAbs = 0.0;
        for (int i = 0; i < length; i++) {
            double abs = Math.abs((int) data[i]);
            if (abs > maxAbs) maxAbs = abs;
        }
        if (maxAbs > 0) {
            double scale = (0.90 * LiveGainProcessor.FULL_SCALE) / maxAbs;
            for (int i = 0; i < length; i++) {
                data[i] = (short) Math.round(data[i] * scale);
            }
        }
        return data;
    }
}
