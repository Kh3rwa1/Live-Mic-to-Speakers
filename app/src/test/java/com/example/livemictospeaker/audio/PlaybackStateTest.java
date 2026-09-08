package com.example.livemictospeaker.audio;

import org.junit.Test;

public final class PlaybackStateTest {
    @Test public void positionsAreBounded() { PlaybackStateChecks.positionsAreBounded(); }
    @Test public void playbackIntentIsConsistent() { PlaybackStateChecks.playbackIntentIsConsistent(); }
    @Test public void snapshotsAndErrorSequenceAreStable() { PlaybackStateChecks.snapshotsAndErrorSequenceAreStable(); }
    @Test public void missingStatusIsRejected() { PlaybackStateChecks.missingStatusIsRejected(); }
}
