package com.word.way.audio;

import org.junit.Test;

public class LivePumpTest {
    @Test public void stopDuringBlockingPumpReleasesPromptly() throws Exception {
        LivePumpChecks.stopDuringBlockingPumpReleasesPromptly();
    }
}
