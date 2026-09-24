package com.word.way.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DriftControllerTest {

    @Test
    public void simulatedClockDriftTriggersCorrection() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384; // 2 bursts

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Simulated drift: input = 48001 Hz, output = 47999 Hz (+2 frames/sec drift)
        // Let's advance time by 100ms steps.
        double inputRate = 48001.0;
        double outputRate = 47999.0;

        long framesWritten = targetQueue;
        long framesPlayed = 0;
        long totalDropped = 0;
        int correctionsTriggered = 0;

        for (int step = 0; step < 200; step++) { // 20 seconds total
            long nowMs = step * 100L;
            double tSec = nowMs / 1000.0;

            // Frames produced and consumed up to this timestamp
            framesWritten = targetQueue + (long) (tSec * inputRate) - totalDropped;
            framesPlayed = (long) (tSec * outputRate);

            int drop = controller.evaluate(framesWritten, framesPlayed, nowMs);
            if (drop > 0) {
                correctionsTriggered++;
                totalDropped += drop;
                assertTrue("Drop must be at most 1 burst", drop <= burstFrames);
                assertTrue("Drop must be <= 2ms (96 frames at 48k)", drop <= 96);
            }
        }

        assertTrue("Simulated drift must trigger at least one correction", correctionsTriggered > 0);
        assertEquals(correctionsTriggered, controller.getTotalCorrections());
    }

    @Test
    public void synchronizedClocksNeverTriggerCorrection() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384;

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Clocks perfectly matched at 48000 Hz
        for (int step = 0; step < 600; step++) { // 60 seconds
            long nowMs = step * 100L;
            long written = targetQueue + (long) (nowMs * 48.0);
            long played = (long) (nowMs * 48.0);

            int drop = controller.evaluate(written, played, nowMs);
            assertEquals("Synchronized clocks must never trigger corrections", 0, drop);
        }

        assertEquals(0, controller.getTotalCorrections());
    }

    @Test
    public void transientJitterDoesNotTriggerCorrection() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384;

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Queue spikes above target for 500 ms (less than 1.0s sustained threshold)
        for (int step = 0; step < 5; step++) {
            long nowMs = step * 100L;
            int drop = controller.evaluate(targetQueue + 50, 0, nowMs);
            assertEquals(0, drop);
        }

        // Drops back to target or below
        int drop = controller.evaluate(targetQueue, 0, 600L);
        assertEquals(0, drop);

        // Stays for another 500 ms: still no trigger because window reset
        for (int step = 7; step < 12; step++) {
            long nowMs = step * 100L;
            int nextDrop = controller.evaluate(targetQueue + 50, 0, nowMs);
            assertEquals(0, nextDrop);
        }

        assertEquals(0, controller.getTotalCorrections());
    }

    @Test
    public void rateLimitEnforcesMinimumInterval() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384;

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Start above target at 0 ms
        assertEquals(0, controller.evaluate(500, 0, 0L));

        // Hold above target for 1100 ms -> triggers correction at 1100 ms
        int drop1 = controller.evaluate(500, 0, 1100L);
        assertTrue("Drop must be > 0 after 1100ms sustained", drop1 > 0);

        // At 1200 ms (only 100 ms later, < 250 ms min interval) -> must not trigger
        int drop2 = controller.evaluate(500, 0, 1200L);
        assertEquals(0, drop2);
    }

    @Test
    public void applyCrossfadeDropSmoothsTransition() {
        int rate = 48000;
        short[] sine = TestSignals.sine(rate, 1000, 0.70, 0.01); // 480 samples
        short[] buffer = new short[192];
        System.arraycopy(sine, 0, buffer, 0, 192);

        int dropFrames = 96;
        int crossfadeFrames = 48;
        int newCount = DriftController.applyCrossfadeDrop(buffer, 192, dropFrames, crossfadeFrames);

        assertEquals(96, newCount);

        // Check for continuity around the crossfade region
        for (int i = 1; i < newCount; i++) {
            int stepDiff = Math.abs((int) buffer[i] - (int) buffer[i - 1]);
            // For 1 kHz sine at 0.70 FS (amplitude ~22937), max sample-to-sample difference is ~3000
            // A click would produce a jump > 20000.
            assertTrue("Step difference at sample " + i + " was " + stepDiff + " (must not click)", stepDiff < 10000);
        }
    }

    @Test
    public void headWrapHandledProperly() {
        DriftController controller = new DriftController(192, 48000, 384);

        long nearMax = 0xFFFFFF80L; // 128 frames before 32-bit wrap
        long unwrapped1 = controller.unwrapHeadPosition(nearMax);
        assertEquals(nearMax, unwrapped1);

        long wrappedVal = 50L; // Wrapped past 0
        long unwrapped2 = controller.unwrapHeadPosition(wrappedVal);
        long expected = (1L << 32) | wrappedVal;
        assertEquals(expected, unwrapped2);
    }

    @Test
    public void degenerateInputsHandledSafely() {
        short[] buf = new short[10];
        assertEquals(0, DriftController.applyCrossfadeDrop(null, 10, 5, 2));
        assertEquals(0, DriftController.applyCrossfadeDrop(buf, 0, 5, 2));
        assertEquals(10, DriftController.applyCrossfadeDrop(buf, 10, 0, 2));
    }
}
