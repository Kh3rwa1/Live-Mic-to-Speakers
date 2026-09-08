package com.word.way.audio;

import org.junit.Test;

public class AudioReliabilityTest {
    @Test public void unusedScreenDoesNotAllocateAudio() throws Exception { ReliabilityChecks.unusedScreen(); }
    @Test public void stopReleasesResourcesOnce() throws Exception { ReliabilityChecks.stopReleasesOnce(); }
    @Test public void restartCannotOverlapOldCleanup() throws Exception { ReliabilityChecks.restartWaitsForCleanup(); }
    @Test public void startFailureReleasesResources() throws Exception { ReliabilityChecks.failureReleases(true); }
    @Test public void deviceFailureReleasesResources() throws Exception { ReliabilityChecks.failureReleases(false); }
    @Test public void factoryFailureIsReported() throws Exception { ReliabilityChecks.factoryFailure(); }
    @Test public void cancellationDuringPreparationNeverStartsMic() throws Exception { ReliabilityChecks.cancelDuringPreparation(); }
    @Test public void onlyValidRecordingsAreKept() { ReliabilityChecks.recordingValidity(); }
}
