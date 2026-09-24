package com.word.way.audio;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JVM unit tests and micro-benchmark for {@link LiveGainProcessor}.
 * Verifies ramp startup, soft limiting, and block-based raw acoustic feedback detection.
 */
public class LiveGainProcessorTest {

    private static short[] filled(int count, int value) {
        short[] data = new short[count];
        java.util.Arrays.fill(data, (short) value);
        return data;
    }

    /** Helper that processes a signal in chunks of bufferSize and returns the triggered sample index, or -1. */
    private static int runSignal(LiveGainProcessor processor, short[] signal, int bufferSize) {
        int pos = 0;
        int triggerIndex = -1;
        short[] chunk = new short[bufferSize];
        while (pos < signal.length) {
            int toRead = Math.min(bufferSize, signal.length - pos);
            System.arraycopy(signal, pos, chunk, 0, toRead);
            if (processor.process(chunk, toRead)) {
                // Find trigger sample within chunk: samples after triggerSampleIndex were zeroed
                int triggerOffset = toRead - 1;
                while (triggerOffset >= 0 && chunk[triggerOffset] == 0) {
                    triggerOffset--;
                }
                triggerIndex = pos + Math.max(0, triggerOffset);
                break;
            }
            pos += toRead;
        }
        return triggerIndex;
    }

    @Test public void rampReachesUserGainAfterStartupWindow() {
        LiveGainProcessor processor = new LiveGainProcessor(1000); // 300-sample ramp
        short[] data = filled(400, 1000);
        processor.process(data, data.length);
        assertEquals("First sample must start from silence", 0, data[0]);
        for (int i = 1; i < 300; i++) {
            assertTrue("Ramp must not decrease", data[i] >= data[i - 1]);
        }
        assertEquals("Ramp must reach the user gain", 1000, data[300]);
        assertEquals(1000, data[399]);
    }

    @Test public void userGainIsClampedToUnitRange() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        processor.setUserGain(5f);
        assertEquals(1f, processor.getUserGain(), 0.0001f);
        processor.setUserGain(-2f);
        assertEquals(0f, processor.getUserGain(), 0.0001f);
    }

    @Test public void limiterNeverReachesFullScaleOrInverts() {
        assertEquals(0, LiveGainProcessor.softLimit(0f));
        assertEquals(1000, LiveGainProcessor.softLimit(1000f));
        short loud = LiveGainProcessor.softLimit(LiveGainProcessor.FULL_SCALE);
        short quiet = LiveGainProcessor.softLimit(-LiveGainProcessor.FULL_SCALE);
        assertTrue("Positive peaks must stay below full scale", loud > 0 && loud < LiveGainProcessor.FULL_SCALE);
        assertTrue("Negative peaks must stay above -full scale", quiet < 0 && quiet > -LiveGainProcessor.FULL_SCALE);
        assertTrue(LiveGainProcessor.softLimit(20000f) <= LiveGainProcessor.softLimit(25000f));
        assertTrue(LiveGainProcessor.softLimit(25000f) <= LiveGainProcessor.softLimit(32767f));
        assertTrue(LiveGainProcessor.softLimit(-20000f) >= LiveGainProcessor.softLimit(-32767f));
    }

    @Test public void emptyInputIsIgnored() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        assertFalse(processor.process(null, 0));
        assertFalse(processor.process(new short[0], 0));
    }

    // T1: sine 1 kHz @ 0.70 FS, 1.5 s, gain 1.0 -> triggers between 0.8 s and 1.2 s
    @Test public void t1_sine1kHzTriggersWithinWindow() {
        for (int rate : new int[]{48000, 44100, 16000}) {
            LiveGainProcessor processor = new LiveGainProcessor(rate);
            processor.setUserGain(1.0f);
            short[] signal = TestSignals.sine(rate, 1000, 0.70, 1.5);
            int triggerIndex = runSignal(processor, signal, 480);
            assertTrue("T1 at " + rate + " Hz must trigger", triggerIndex >= 0);
            double triggerSec = (double) triggerIndex / rate;
            assertTrue("Trigger time " + triggerSec + "s must be >= 0.8s", triggerSec >= 0.8);
            assertTrue("Trigger time " + triggerSec + "s must be <= 1.2s", triggerSec <= 1.2);
            assertTrue(processor.isFeedbackLatched());
            assertTrue(processor.isMuted());
        }
    }

    // T2: same as T1 with gain 0.8 -> triggers (proves D2 is fixed)
    @Test public void t2_sine1kHzWithDefaultGain08Triggers() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        processor.setUserGain(0.8f);
        short[] signal = TestSignals.sine(rate, 1000, 0.70, 1.5);
        int triggerIndex = runSignal(processor, signal, 480);
        assertTrue("T2 must trigger at default gain 0.8", triggerIndex >= 0);
        double triggerSec = (double) triggerIndex / rate;
        assertTrue("Trigger time " + triggerSec + "s must be within [0.8s, 1.2s]", triggerSec >= 0.8 && triggerSec <= 1.2);
    }

    // T3: same as T1 with gain 0.3 -> triggers (analysis is pre-gain)
    @Test public void t3_sine1kHzWithLowGain03Triggers() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        processor.setUserGain(0.3f);
        short[] signal = TestSignals.sine(rate, 1000, 0.70, 1.5);
        int triggerIndex = runSignal(processor, signal, 480);
        assertTrue("T3 must trigger with low gain 0.3", triggerIndex >= 0);
        double triggerSec = (double) triggerIndex / rate;
        assertTrue("Trigger time " + triggerSec + "s must be within [0.8s, 1.2s]", triggerSec >= 0.8 && triggerSec <= 1.2);
    }

    // T4: same as T1 with gain 0.0 -> triggers.
    // Documented rationale: Analysis operates on RAW input samples pre-gain.
    // When an acoustic feedback loop is occurring on the input channel, the danger is present
    // even if output monitoring gain is currently 0.0. Detecting pre-gain ensures the session
    // safely shuts down before the user raises the volume slider and gets blasted.
    @Test public void t4_sine1kHzWithZeroGainTriggersPreGain() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        processor.setUserGain(0.0f);
        short[] signal = TestSignals.sine(rate, 1000, 0.70, 1.5);
        int triggerIndex = runSignal(processor, signal, 480);
        assertTrue("T4 must trigger with gain 0.0 because raw analysis is pre-gain", triggerIndex >= 0);
    }

    // T5: sine 3 kHz @ 0.85 FS -> triggers
    @Test public void t5_sine3kHzTriggers() {
        for (int rate : new int[]{48000, 44100, 16000}) {
            LiveGainProcessor processor = new LiveGainProcessor(rate);
            short[] signal = TestSignals.sine(rate, 3000, 0.85, 1.5);
            int triggerIndex = runSignal(processor, signal, 480);
            assertTrue("T5 at " + rate + " Hz must trigger", triggerIndex >= 0);
            double triggerSec = (double) triggerIndex / rate;
            assertTrue("Trigger time " + triggerSec + "s must be within [0.8s, 1.2s]", triggerSec >= 0.8 && triggerSec <= 1.2);
        }
    }

    // T6: sine 400 Hz @ 0.75 FS -> triggers
    @Test public void t6_sine400HzTriggers() {
        for (int rate : new int[]{48000, 44100, 16000}) {
            LiveGainProcessor processor = new LiveGainProcessor(rate);
            short[] signal = TestSignals.sine(rate, 400, 0.75, 1.5);
            int triggerIndex = runSignal(processor, signal, 480);
            assertTrue("T6 at " + rate + " Hz must trigger", triggerIndex >= 0);
            double triggerSec = (double) triggerIndex / rate;
            assertTrue("Trigger time " + triggerSec + "s must be within [0.8s, 1.2s]", triggerSec >= 0.8 && triggerSec <= 1.2);
        }
    }

    // T7: speechLike, 10 s -> never triggers
    @Test public void t7_speechLikeNeverTriggers() {
        for (int rate : new int[]{48000, 44100, 16000}) {
            LiveGainProcessor processor = new LiveGainProcessor(rate);
            short[] signal = TestSignals.speechLike(rate, 10.0, 42L);
            int triggerIndex = runSignal(processor, signal, 480);
            assertEquals("T7 at " + rate + " Hz must never trigger for speechLike signal", -1, triggerIndex);
            assertFalse(processor.isFeedbackLatched());
        }
    }

    // T8: shoutLike, 5 s -> never triggers (tonality criterion)
    @Test public void t8_shoutLikeNeverTriggers() {
        for (int rate : new int[]{48000, 44100, 16000}) {
            LiveGainProcessor processor = new LiveGainProcessor(rate);
            short[] signal = TestSignals.shoutLike(rate, 5.0, 123L);
            int triggerIndex = runSignal(processor, signal, 480);
            assertEquals("T8 at " + rate + " Hz must never trigger for shoutLike signal", -1, triggerIndex);
            assertFalse(processor.isFeedbackLatched());
        }
    }

    // T9: loud sine burst of 0.5 s followed by 2 s of silence -> never triggers, and score decays to 0
    @Test public void t9_loudSineBurstFollowedBySilenceDecaysScoreToZero() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        short[] burst = TestSignals.sine(rate, 1000, 0.70, 0.5);
        short[] silence = TestSignals.silence(rate, 2.0);

        int triggerBurst = runSignal(processor, burst, 480);
        assertEquals("0.5s burst must not trigger", -1, triggerBurst);
        assertTrue("Score after burst must be positive", processor.getFeedbackScore() > 0);

        int triggerSilence = runSignal(processor, silence, 480);
        assertEquals("Silence must not trigger", -1, triggerSilence);
        assertEquals("Score after 2s silence must decay to 0", 0, processor.getFeedbackScore());
    }

    // T10: repeated pattern of 0.6 s loud tone / 0.4 s silence for 10 s -> triggers eventually
    // Expected behavior: Each 0.6 s tone adds +120 to score; each 0.4 s silence subtracts -40.
    // Net accumulation is +80 per 1.0 s cycle. The tone is mostly present (60% duty cycle) and sustained,
    // so it should trigger during the second cycle (~1.6 s total).
    @Test public void t10_repeatedToneSilencePatternTriggersEventually() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        short[] tone = TestSignals.sine(rate, 1000, 0.70, 0.6);
        short[] silence = TestSignals.silence(rate, 0.4);

        boolean triggered = false;
        int totalSamples = 0;
        int triggerSample = -1;
        for (int cycle = 0; cycle < 10 && !triggered; cycle++) {
            int tIndex = runSignal(processor, tone, 480);
            if (tIndex >= 0) {
                triggered = true;
                triggerSample = totalSamples + tIndex;
                break;
            }
            totalSamples += tone.length;
            int sIndex = runSignal(processor, silence, 480);
            if (sIndex >= 0) {
                triggered = true;
                triggerSample = totalSamples + sIndex;
                break;
            }
            totalSamples += silence.length;
        }
        assertTrue("T10 mostly-present tone must trigger", triggered);
        double triggerSec = (double) triggerSample / rate;
        assertTrue("Trigger time " + triggerSec + "s should be around cycle 2 (1.4s - 2.0s)",
                triggerSec >= 1.2 && triggerSec <= 2.2);
    }

    // T11: lowNoise 10 s and silence 10 s -> never trigger
    @Test public void t11_lowNoiseAndSilenceNeverTrigger() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        short[] noise = TestSignals.lowNoise(rate, 10.0, 999L);
        assertEquals(-1, runSignal(processor, noise, 480));
        assertFalse(processor.isFeedbackLatched());

        short[] silence = TestSignals.silence(rate, 10.0);
        assertEquals(-1, runSignal(processor, silence, 480));
        assertFalse(processor.isFeedbackLatched());
    }

    // T12: buffer-size invariance: T1 fed at 64, 192, 256, 480, 960 and 4096 samples triggers within +- 1 block
    @Test public void t12_bufferSizeInvariance() {
        int rate = 48000;
        int blockLength = rate / 100; // 480 samples
        int[] bufferSizes = new int[]{64, 192, 256, 480, 960, 4096};
        int baselineTrigger = -1;

        for (int size : bufferSizes) {
            LiveGainProcessor processor = new LiveGainProcessor(rate);
            short[] signal = TestSignals.sine(rate, 1000, 0.70, 1.5);
            int trigger = runSignal(processor, signal, size);
            assertTrue("Must trigger with buffer size " + size, trigger >= 0);
            if (baselineTrigger == -1) {
                baselineTrigger = trigger;
            } else {
                int diff = Math.abs(trigger - baselineTrigger);
                assertTrue("Trigger index diff " + diff + " for buffer size " + size + " must be <= blockLength (" + blockLength + ")",
                        diff <= blockLength);
            }
        }
    }

    // T13: trigger mid-buffer: returned boolean true only for buffer containing trigger; samples after trigger
    // point are 0; later buffers all 0; process() never returns true again until reset().
    @Test public void t13_triggerMidBufferContract() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        short[] signal = TestSignals.sine(rate, 1000, 0.70, 1.5);

        int bufferSize = 960;
        short[] buffer = new short[bufferSize];
        int pos = 0;
        boolean triggeredOnce = false;

        while (pos < signal.length) {
            int toRead = Math.min(bufferSize, signal.length - pos);
            System.arraycopy(signal, pos, buffer, 0, toRead);
            boolean result = processor.process(buffer, toRead);
            if (result) {
                assertFalse("process() must return true only once", triggeredOnce);
                triggeredOnce = true;
                // Verify that samples after the trigger point are zeroed
                int triggerIndex = toRead - 1;
                while (triggerIndex >= 0 && buffer[triggerIndex] == 0) triggerIndex--;
                assertTrue("Trigger should happen within the buffer", triggerIndex >= 0 && triggerIndex < toRead);
                for (int i = triggerIndex + 1; i < toRead; i++) {
                    assertEquals("Samples after trigger must be zero", 0, buffer[i]);
                }
            } else if (triggeredOnce) {
                // Later buffers must be all zeros
                for (int i = 0; i < toRead; i++) {
                    assertEquals("Later buffers must be zeroed", 0, buffer[i]);
                }
            }
            pos += toRead;
        }
        assertTrue("Must have triggered", triggeredOnce);
    }

    // T14: reset(): after trigger, reset() then T7 -> no trigger; ramp restarts from 0
    @Test public void t14_resetClearsLatchAndRestartsRamp() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        short[] sine = TestSignals.sine(rate, 1000, 0.70, 1.5);
        assertTrue(runSignal(processor, sine, 480) >= 0);
        assertTrue(processor.isFeedbackLatched());
        assertTrue(processor.isMuted());

        processor.reset();
        assertFalse(processor.isFeedbackLatched());
        assertFalse(processor.isMuted());
        assertEquals(0, processor.getFeedbackScore());

        // Feeding speechLike after reset should not trigger
        short[] speech = TestSignals.speechLike(rate, 3.0, 42L);
        assertEquals(-1, runSignal(processor, speech, 480));

        // After reset, ramp restarts from 0
        processor.reset();
        short[] step = filled(400, 1000);
        processor.process(step, step.length);
        assertEquals("First sample must be 0 after reset", 0, step[0]);
    }

    // T15: Limiter invariants stay the same: output magnitude < 32767 for any input and gain, monotonic
    @Test public void t15_limiterInvariants() {
        LiveGainProcessor processor = new LiveGainProcessor(48000);
        processor.setUserGain(1.0f);
        short[] loud = filled(1000, LiveGainProcessor.FULL_SCALE);
        processor.process(loud, loud.length);
        for (short s : loud) {
            assertTrue("Output magnitude must stay strictly below full scale", Math.abs((int) s) < LiveGainProcessor.FULL_SCALE);
        }
    }

    @Test public void variousBurstSizesHandleFeedbackAndSpeechCorrectly() {
        int[] burstSizes = {96, 144, 192, 240, 256};
        int rate = 48000;
        for (int burst : burstSizes) {
            // Test 1: Feedback sine wave must trigger between 0.8s and 1.2s
            LiveGainProcessor fbProc = new LiveGainProcessor(rate);
            fbProc.setUserGain(1.0f);
            short[] sine = TestSignals.sine(rate, 1000, 0.70, 1.5);
            int triggerIndex = runSignal(fbProc, sine, burst);
            assertTrue("Sine feedback at burst " + burst + " must trigger", triggerIndex >= 0);
            double triggerSec = (double) triggerIndex / rate;
            assertTrue("Trigger time " + triggerSec + "s for burst " + burst + " must be >= 0.8s", triggerSec >= 0.8);
            assertTrue("Trigger time " + triggerSec + "s for burst " + burst + " must be <= 1.2s", triggerSec <= 1.2);
            assertTrue(fbProc.isFeedbackLatched());
            assertTrue(fbProc.isMuted());

            // Test 2: Speech-like audio must NOT trigger
            LiveGainProcessor speechProc = new LiveGainProcessor(rate);
            speechProc.setUserGain(1.0f);
            short[] speech = TestSignals.speechLike(rate, 3.0, 1234L);
            int speechTrigger = runSignal(speechProc, speech, burst);
            assertEquals("Speech must not trigger at burst " + burst, -1, speechTrigger);
            assertFalse(speechProc.isFeedbackLatched());
            assertFalse(speechProc.isMuted());

            // Test 3: Ramp must work across bursts
            LiveGainProcessor rampProc = new LiveGainProcessor(1000); // 300 samples ramp
            short[] step = filled(400, 1000);
            int pos = 0;
            short[] chunk = new short[burst];
            while (pos < step.length) {
                int toRead = Math.min(burst, step.length - pos);
                System.arraycopy(step, pos, chunk, 0, toRead);
                rampProc.process(chunk, toRead);
                System.arraycopy(chunk, 0, step, pos, toRead);
                pos += toRead;
            }
            assertEquals("First sample must be silence for burst " + burst, 0, step[0]);
            assertEquals("Ramp must reach target at sample 300 for burst " + burst, 1000, step[300]);
            assertEquals(1000, step[399]);
        }
    }

    // JVM micro-benchmark: process 60 s of 48 kHz audio and print elapsed time
    @Test public void microBenchmark60sAudio() {
        int rate = 48000;
        LiveGainProcessor processor = new LiveGainProcessor(rate);
        processor.setUserGain(0.8f);
        short[] speech = TestSignals.speechLike(rate, 60.0, 12345L);
        short[] chunk = new short[480];

        long startNs = System.nanoTime();
        int pos = 0;
        while (pos < speech.length) {
            int toRead = Math.min(chunk.length, speech.length - pos);
            System.arraycopy(speech, pos, chunk, 0, toRead);
            processor.process(chunk, toRead);
            pos += toRead;
        }
        long elapsedNs = System.nanoTime() - startNs;
        double elapsedMs = elapsedNs / 1_000_000.0;
        System.out.println("=== LiveGainProcessor Micro-benchmark ===");
        System.out.println("Processed 60.0s of 48 kHz audio in: " + elapsedMs + " ms (" + String.format("%.2f", 60000.0 / elapsedMs) + "x real-time)");
        System.out.println("=========================================");
    }
}
