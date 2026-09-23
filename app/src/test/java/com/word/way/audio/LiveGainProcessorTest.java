package com.word.way.audio;

import org.junit.Test;
import static org.junit.Assert.*;

/** JVM coverage for the live gain ramp, limiter and feedback detector. */
public class LiveGainProcessorTest {
    private static short[] filled(int count, int value) {
        short[] data = new short[count];
        java.util.Arrays.fill(data, (short) value);
        return data;
    }

    @Test public void rampReachesUserGainAfterStartupWindow() {
        LiveGainProcessor processor = new LiveGainProcessor(1000); // 300-sample ramp
        short[] data = filled(400, 1000);
        processor.process(data, data.length);
        assertEquals("First sample must start from silence", 0, data[0]);
        // Constant below the limiter knee, so the output tracks the ramp directly.
        for (int i = 1; i < 300; i++) {
            assertTrue("Ramp must not decrease", data[i] >= data[i - 1]);
        }
        assertEquals("Ramp must reach the user gain", 1000, data[300]);
        assertEquals(1000, data[399]);
    }

    @Test public void zeroGainProducesSilenceAndNoFeedback() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        processor.setUserGain(0f);
        short[] data = filled(2000, LiveGainProcessor.FULL_SCALE);
        assertFalse(processor.process(data, data.length));
        for (short sample : data) assertEquals(0, sample);
        assertFalse(processor.isFeedbackLatched());
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
        // Monotonic: a larger input can never produce a smaller limited output.
        assertTrue(LiveGainProcessor.softLimit(20000f) <= LiveGainProcessor.softLimit(25000f));
        assertTrue(LiveGainProcessor.softLimit(25000f) <= LiveGainProcessor.softLimit(32767f));
        assertTrue(LiveGainProcessor.softLimit(-20000f) >= LiveGainProcessor.softLimit(-32767f));
    }

    @Test public void sustainedFullScaleTriggersFeedbackOnceAndMutes() {
        LiveGainProcessor processor = new LiveGainProcessor(1000); // 1000-sample feedback window
        assertTrue("Sustained full-scale input must latch feedback",
                processor.process(filled(2000, LiveGainProcessor.FULL_SCALE), 2000));
        assertTrue(processor.isFeedbackLatched());
        assertTrue(processor.isMuted());
        short[] again = filled(100, 1000);
        assertFalse("Feedback must only be reported on the triggering buffer",
                processor.process(again, again.length));
        for (short sample : again) assertEquals("Latched feedback must keep output muted", 0, sample);
    }

    @Test public void loudBurstShorterThanWindowDoesNotTrigger() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        assertFalse(processor.process(filled(500, LiveGainProcessor.FULL_SCALE), 500));
        assertFalse(processor.isFeedbackLatched());
    }

    @Test public void moderateSustainedLevelDoesNotTrigger() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        // 60% of full scale is loud but well below the feedback threshold.
        short[] data = filled(2000, (int) (LiveGainProcessor.FULL_SCALE * 0.6f));
        assertFalse(processor.process(data, data.length));
        assertFalse(processor.isFeedbackLatched());
    }

    @Test public void resetClearsLatchAndRestartsRamp() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        processor.process(filled(2000, LiveGainProcessor.FULL_SCALE), 2000);
        assertTrue(processor.isFeedbackLatched());
        processor.reset();
        assertFalse(processor.isFeedbackLatched());
        assertFalse(processor.isMuted());
        short[] data = filled(400, 1000);
        processor.process(data, data.length);
        assertEquals("Reset must restart the ramp from silence", 0, data[0]);
        assertEquals(1000, data[300]);
    }

    @Test public void emptyInputIsIgnored() {
        LiveGainProcessor processor = new LiveGainProcessor(1000);
        assertFalse(processor.process(null, 0));
        assertFalse(processor.process(new short[0], 0));
    }
}
