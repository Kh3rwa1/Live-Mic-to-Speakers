package com.word.way.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DriftControllerTest {

    @Test
    public void simulatedClockDriftKeepsBacklogBounded() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384; // 2 bursts

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Simulated clock drift: mic clock is 0.1% faster than output
        // micRate = 48048 Hz, outputRate = 48000 Hz (+48 frames/sec drift)
        double micRate = 48048.0;
        double outputRate = 48000.0;

        long totalFramesRead = 0;
        long totalDropped = 0;
        int correctionsTriggered = 0;

        // Simulate 60 seconds of playback in 4 ms (192 frame) steps
        int totalSteps = 15000; // 60s / 0.004s = 15000 steps
        for (int step = 1; step <= totalSteps; step++) {
            double tSec = step * 0.004;
            long nowMs = (long) (tSec * 1000.0);

            long micFramesProduced = (long) (tSec * micRate);
            long outputFramesConsumed = (long) (tSec * outputRate);

            // Read follows output consumption plus any backlog drained by previous drops
            totalFramesRead = outputFramesConsumed + totalDropped;
            long backlog = Math.max(0L, micFramesProduced - totalFramesRead);

            // AudioTrack head advances at outputRate
            long framesWritten = outputFramesConsumed;
            long rawHead = outputFramesConsumed;

            int drop = controller.evaluate(backlog, framesWritten, rawHead, nowMs, false, false);
            if (drop > 0) {
                correctionsTriggered++;
                totalDropped += drop;
                assertTrue("Drop must be at most 1 burst (" + burstFrames + ")", drop <= burstFrames);
                assertTrue("Drop must be <= 2ms (96 frames at 48k)", drop <= 96);
            }
        }

        long finalBacklog = (long) (60.0 * micRate) - totalFramesRead;
        assertTrue("Drift controller must have triggered corrections", correctionsTriggered > 0);
        assertEquals(correctionsTriggered, controller.getTotalCorrections());
        // Without drift correction, backlog would be 60 * 48 = 2880 frames (60 ms).
        // With drift correction, backlog stays strictly bounded near threshold (384 frames + 1s drift of 48 = 432 frames).
        assertTrue("Input backlog must stay bounded (< 500 frames), was " + finalBacklog, finalBacklog < 500);
    }

    @Test
    public void equalClocksWithStepwiseHeadPositionZeroCorrectionsOver60s() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384; // 2 bursts

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Clocks perfectly matched at 48000 Hz over 60 seconds
        int totalSteps = 15000; // 60s in 4ms steps
        for (int step = 1; step <= totalSteps; step++) {
            long nowMs = step * 4L;
            long totalFramesRead = step * 192L;
            long micFramesProduced = (nowMs * 48000L) / 1000L;
            long backlog = Math.max(0L, micFramesProduced - totalFramesRead);

            // Output playback head position updates in steps (e.g. every 8 bursts = 1536 frames)
            long rawHead = (step / 8) * (8L * burstFrames);
            long framesWritten = step * (long) burstFrames;

            int drop = controller.evaluate(backlog, framesWritten, rawHead, nowMs, false, false);
            assertEquals("Equal clocks must never trigger corrections even with stepwise head position", 0, drop);
        }

        assertEquals(0, controller.getTotalCorrections());
    }

    @Test
    public void spikeShorterThanOneSecondProducesNoDrop() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384;

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);
        int threshold = burstFrames * 2; // 384 frames

        // Backlog spikes to 500 frames (> 384 threshold) for 800 ms (< 1.0 s)
        for (int step = 0; step < 8; step++) {
            long nowMs = step * 100L;
            int drop = controller.evaluate(500, 1000, 1000, nowMs, false, false);
            assertEquals(0, drop);
        }

        // Backlog drops below threshold at 850 ms
        int dropReset = controller.evaluate(threshold - 50, 1000, 1000, 850L, false, false);
        assertEquals(0, dropReset);

        // Backlog spikes again for another 600 ms (< 1.0 s)
        for (int step = 9; step <= 15; step++) {
            long nowMs = step * 100L;
            int drop = controller.evaluate(500, 1000, 1000, nowMs, false, false);
            assertEquals(0, drop);
        }

        assertEquals("Transient spikes under 1s must never trigger corrections", 0, controller.getTotalCorrections());
    }

    @Test
    public void noDropsDuringRampOrAfterFeedbackLatched() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384;

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Sustained backlog of 600 frames (> 384 threshold) for 2.0 s, but during ramp
        for (int step = 0; step < 20; step++) {
            long nowMs = step * 100L;
            int drop = controller.evaluate(600, 1000, 1000, nowMs, true /* isRamping */, false);
            assertEquals("No drops during ramp", 0, drop);
        }
        assertEquals(0, controller.getTotalCorrections());

        // Sustained backlog of 600 frames for 2.0 s, but feedback is latched
        for (int step = 20; step < 40; step++) {
            long nowMs = step * 100L;
            int drop = controller.evaluate(600, 1000, 1000, nowMs, false, true /* feedbackLatched */);
            assertEquals("No drops after feedback latched", 0, drop);
        }
        assertEquals(0, controller.getTotalCorrections());

        // When ramp and feedback latch are inactive, sustained drift triggers correction after 1s
        controller.evaluate(600, 1000, 1000, 4000L, false, false);
        int drop = controller.evaluate(600, 1000, 1000, 5100L, false, false);
        assertTrue("Drop must trigger when not ramping and not latched", drop > 0);
        assertEquals(1, controller.getTotalCorrections());
    }

    @Test
    public void diagnosticOutputCheckIncludesOneBurstMargin() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384; // 2 bursts

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Output queue at targetQueue + 1 (385 frames) -> below target + burst (576 frames)
        controller.evaluate(0, 385, 0, 100L, false, false);
        assertFalse("Output diagnostic must include 1-burst margin", controller.isOutputQueueAboveDiagnosticThreshold());

        // Output queue at targetQueue + burst (576 frames) -> not strictly above
        controller.evaluate(0, 576, 0, 200L, false, false);
        assertFalse(controller.isOutputQueueAboveDiagnosticThreshold());

        // Output queue at targetQueue + burst + 1 (577 frames) -> strictly above
        controller.evaluate(0, 577, 0, 300L, false, false);
        assertTrue(controller.isOutputQueueAboveDiagnosticThreshold());
    }

    @Test
    public void rateLimitEnforcesMinimumInterval() {
        int sampleRate = 48000;
        int burstFrames = 192;
        int targetQueue = 384;

        DriftController controller = new DriftController(burstFrames, sampleRate, targetQueue, 0L);

        // Start above target (500 frames backlog > 384 threshold) at 0 ms
        assertEquals(0, controller.evaluate(500, 0, 0, 0L, false, false));

        // Hold above target for 1100 ms -> triggers correction at 1100 ms
        int drop1 = controller.evaluate(500, 0, 0, 1100L, false, false);
        assertTrue("Drop must be > 0 after 1100ms sustained", drop1 > 0);

        // At 1200 ms (only 100 ms later, < 250 ms min interval) -> must not trigger
        int drop2 = controller.evaluate(500, 0, 0, 1200L, false, false);
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
