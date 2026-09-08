package com.word.way.audio;

import org.junit.Test;

public class RecordingRuntimeTest {
    @Test public void levelBounds() { RecordingRuntimeChecks.levelBounds(); }
    @Test public void runtimeFailureDeliveredOnce() throws Exception { RecordingRuntimeChecks.runtimeFailureDeliveredOnce(); }
    @Test public void staleFailureCannotStopRestart() throws Exception { RecordingRuntimeChecks.staleFailureCannotStopRestart(); }
    @Test public void failureDuringPreparationCancelsCapture() throws Exception { RecordingRuntimeChecks.failureDuringPreparationCancelsCapture(); }
    @Test public void meterFailureStopsSession() throws Exception { RecordingRuntimeChecks.meterFailureStopsSession(); }
    @Test public void lateLevelsAndClosedUiAreSuppressed() throws Exception { RecordingRuntimeChecks.lateLevelsAndClosedUiAreSuppressed(); }
    @Test public void queuedSaveSurvivesClose() throws Exception { RecordingRuntimeChecks.queuedSaveSurvivesClose(); }
    @Test public void cancellationAndRapidRestarts() throws Exception { RecordingRuntimeChecks.cancellationAndRapidRestarts(); }
}
