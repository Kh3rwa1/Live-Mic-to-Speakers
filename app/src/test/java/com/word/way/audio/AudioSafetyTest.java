package com.word.way.audio;

import org.junit.Test;

public class AudioSafetyTest {
    @Test public void interruptionsAreOneShotAndCloseIsFinal() { AudioSafetyChecks.oneInterruptionPerSession(); }
    @Test public void simultaneousRouteAndFocusLossDeliverOnce() throws Exception { AudioSafetyChecks.simultaneousInterruptionsDeliverOnce(); }
    @Test public void stopDuringPreparationPreventsLateCapture() throws Exception { AudioSafetyChecks.cancelledPreparationDoesNotStartCapture(); }
}
