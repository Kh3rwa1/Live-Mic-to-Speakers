package com.example.livemictospeaker.audio;

/** Pure-Java regression checks; also executable without the Android SDK. */
public final class PlaybackStateChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void positionsAreBounded() {
        PlaybackState negative = new PlaybackState(PlaybackState.Status.PAUSED, -10, -20, false);
        check(negative.getPositionMs() == 0 && negative.getDurationMs() == 0, "Negative media time leaked");
        PlaybackState oversized = new PlaybackState(PlaybackState.Status.PLAYING, Integer.MAX_VALUE, 1000, true);
        check(oversized.getPositionMs() == 1000, "Position exceeded duration");
        PlaybackState preparing = new PlaybackState(PlaybackState.Status.PREPARING, 700, 0, false);
        check(preparing.getPositionMs() == 700, "Pending restore position was lost");
    }
    public static void playbackIntentIsConsistent() {
        for (PlaybackState.Status status : PlaybackState.Status.values()) {
            PlaybackState state = new PlaybackState(status, 0, 0, true);
            boolean expected = status == PlaybackState.Status.PLAYING || status == PlaybackState.Status.PREPARING;
            check(state.isPlaybackRequested() == expected, "Invalid playback intention for " + status);
        }
    }
    public static void snapshotsAndErrorSequenceAreStable() {
        PlaybackState old = new PlaybackState(PlaybackState.Status.ERROR, 20, 100, false, 7);
        PlaybackState next = new PlaybackState(PlaybackState.Status.PAUSED, 40, 100, false, 7);
        check(old.getPositionMs() == 20 && next.getPositionMs() == 40, "Snapshots changed each other");
        check(old.getErrorSequence() == next.getErrorSequence(), "Ordinary progress created a new error");
        check(new PlaybackState(PlaybackState.Status.IDLE, 0, 0, false, -1).getErrorSequence() == 0,
                "Negative error sequence leaked");
    }
    public static void missingStatusIsRejected() {
        try {
            new PlaybackState(null, 0, 0, false);
            throw new AssertionError("Null status accepted");
        } catch (NullPointerException expected) { }
    }
    public static void main(String[] args) {
        positionsAreBounded(); playbackIntentIsConsistent(); snapshotsAndErrorSequenceAreStable(); missingStatusIsRejected();
        System.out.println("PASS: playback state boundaries, immutable snapshots, intention and error identity");
    }
}
