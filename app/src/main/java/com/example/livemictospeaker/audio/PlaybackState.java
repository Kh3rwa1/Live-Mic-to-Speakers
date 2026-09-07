package com.example.livemictospeaker.audio;

import java.util.Objects;

/** Immutable playback snapshot; no Activity, player, file contents or callback is retained. */
public final class PlaybackState {
    public enum Status { IDLE, PREPARING, PLAYING, PAUSED, COMPLETED, ERROR }
    private final Status status;
    private final int positionMs;
    private final int durationMs;
    private final boolean playbackRequested;
    private final long errorSequence;

    public PlaybackState(Status status, int positionMs, int durationMs, boolean playbackRequested) {
        this(status, positionMs, durationMs, playbackRequested, 0);
    }
    public PlaybackState(Status status, int positionMs, int durationMs, boolean playbackRequested, long errorSequence) {
        this.errorSequence = Math.max(0, errorSequence);
        this.status = Objects.requireNonNull(status, "status");
        this.durationMs = Math.max(0, durationMs);
        this.positionMs = this.durationMs > 0
                ? Math.min(Math.max(0, positionMs), this.durationMs) : Math.max(0, positionMs);
        this.playbackRequested = playbackRequested && (status == Status.PREPARING || status == Status.PLAYING);
    }
    public long getErrorSequence() { return errorSequence; }
    public Status getStatus() { return status; }
    public int getPositionMs() { return positionMs; }
    public int getDurationMs() { return durationMs; }
    public boolean isPlaybackRequested() { return playbackRequested; }
}
