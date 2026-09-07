package com.example.livemictospeaker.audio;

import org.junit.Test;

public class RecordingControllerTest {
    @Test public void unusedAndClosed() throws Exception { RecordingControllerChecks.unusedAndClosed(); }
    @Test public void cancelDuringFactory() throws Exception { RecordingControllerChecks.cancelDuringFactory(); }
    @Test public void cancelDuringPreparation() throws Exception { RecordingControllerChecks.cancelDuringPreparation(); }
    @Test public void serializedFinalization() throws Exception { RecordingControllerChecks.finalizationSerializesRestart(); }
    @Test public void startFailure() throws Exception { RecordingControllerChecks.startFailure(); }
    @Test public void stopFailure() throws Exception { RecordingControllerChecks.stopFailure(); }
    @Test public void safeDestroy() throws Exception { RecordingControllerChecks.closePreservesQueuedSaveAndSuppressesUi(); }
    @Test public void rapidCycles() throws Exception { RecordingControllerChecks.rapidCycles(); }
}
