package com.word.way.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OutputBufferTunerTest {

    @Test
    public void initialTargetIsTwoBurstsWhenCapacityAllows() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 1920);
        assertEquals(192, tuner.getBurstFrames());
        assertEquals(1920, tuner.getCapacityFrames());
        assertEquals(384, tuner.getMinFrames());
        assertEquals(768, tuner.getMaxFrames());
        assertEquals(384, tuner.getCurrentTargetFrames());
    }

    @Test
    public void initialTargetClampedWhenCapacitySmallerThanTwoBursts() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 300);
        assertEquals(300, tuner.getMinFrames());
        assertEquals(300, tuner.getMaxFrames());
        assertEquals(300, tuner.getCurrentTargetFrames());
    }

    @Test
    public void maxFramesClampedByCapacity() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 500);
        assertEquals(384, tuner.getMinFrames());
        assertEquals(500, tuner.getMaxFrames());
        assertEquals(384, tuner.getCurrentTargetFrames());
    }

    @Test
    public void adaptationGrowsByOneBurstOnUnderrunAfterCooldown() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 1920, 0, 0L);

        // Underrun occurs before cooldown finishes: no adaptation yet
        int resultBeforeCooldown = tuner.onUnderrunCheck(1, 100L);
        assertEquals(-1, resultBeforeCooldown);
        assertEquals(384, tuner.getCurrentTargetFrames());

        // Cooldown expires (250ms), underrun delta is still present: adapt +1 burst (576)
        int resultAfterCooldown = tuner.onUnderrunCheck(1, 250L);
        assertEquals(576, resultAfterCooldown);
        assertEquals(576, tuner.getCurrentTargetFrames());

        // Same underrun count: no change
        assertEquals(-1, tuner.onUnderrunCheck(1, 300L));
        assertEquals(576, tuner.getCurrentTargetFrames());

        // Underrun increases to 2, before new cooldown finishes (250 + 250 = 500ms): no adaptation yet
        assertEquals(-1, tuner.onUnderrunCheck(2, 400L));
        assertEquals(576, tuner.getCurrentTargetFrames());

        // Cooldown expires at 500ms+: adapt to 4 bursts (768)
        int secondAdaptation = tuner.onUnderrunCheck(2, 500L);
        assertEquals(768, secondAdaptation);
        assertEquals(768, tuner.getCurrentTargetFrames());

        // Further underruns cannot exceed max ceiling (768)
        assertEquals(-1, tuner.onUnderrunCheck(3, 800L));
        assertEquals(768, tuner.getCurrentTargetFrames());
    }

    @Test
    public void neverShrinksBelowMinFrames() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 1920);
        assertEquals(384, tuner.getCurrentTargetFrames());

        // Even with lower underrun or negative sizes, never shrinks below 384
        tuner.recordActualSize(200);
        assertEquals(384, tuner.getCurrentTargetFrames());

        tuner.recordActualSize(-1);
        assertEquals(384, tuner.getCurrentTargetFrames());
    }

    @Test
    public void recordActualSizeUpdatesTargetWithinBounds() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 1920);
        tuner.recordActualSize(512);
        assertEquals(512, tuner.getCurrentTargetFrames());

        tuner.recordActualSize(900); // capped at maxFrames 768
        assertEquals(768, tuner.getCurrentTargetFrames());
    }

    @Test
    public void counterResetOrOverflowHandledSafely() {
        OutputBufferTuner tuner = new OutputBufferTuner(192, 1920, 5, 0L);
        // Counter drops to 0 (reset/overflow)
        int res = tuner.onUnderrunCheck(0, 300L);
        assertEquals(-1, res);
        assertEquals(0, tuner.getUnderrunsAtLastAdaptation());

        // Next increment from 0 to 1 should adapt
        int nextRes = tuner.onUnderrunCheck(1, 600L);
        assertEquals(576, nextRes);
    }

    @Test
    public void degenerateInputsHandledSafely() {
        OutputBufferTuner tuner = new OutputBufferTuner(0, 0);
        assertTrue(tuner.getBurstFrames() >= 1);
        assertTrue(tuner.getCapacityFrames() >= 1);
        assertTrue(tuner.getMinFrames() >= 1);
        assertTrue(tuner.getMaxFrames() >= tuner.getMinFrames());
    }
}
