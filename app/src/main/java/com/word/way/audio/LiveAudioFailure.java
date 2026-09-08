package com.word.way.audio;

import java.io.IOException;
import java.io.InterruptedIOException;

/** Stable recovery categories; UI copy never depends on platform exception messages. */
public final class LiveAudioFailure extends IOException {
    private static final long serialVersionUID = 1L;
    public enum Reason {
        PERMISSION, SERVICE_UNAVAILABLE, FOCUS_UNAVAILABLE, UNSUPPORTED_CONFIGURATION,
        MICROPHONE_UNAVAILABLE, READ_FAILED, OUTPUT_FAILED, CANCELLED, UNKNOWN
    }
    private final Reason reason;
    public LiveAudioFailure(Reason reason, String detail) { this(reason, detail, null); }
    public LiveAudioFailure(Reason reason, String detail, Throwable cause) {
        super(detail, cause);
        if (reason == null) throw new IllegalArgumentException("A recovery reason is required");
        this.reason = reason;
    }
    public Reason getReason() { return reason; }
    public static Reason reasonOf(Throwable error) {
        // A bounded walk also tolerates hostile/cyclic exception chains.
        for (int depth = 0; error != null && depth < 32; depth++, error = error.getCause()) {
            if (error instanceof LiveAudioFailure) return ((LiveAudioFailure) error).getReason();
            if (error instanceof SecurityException) return Reason.PERMISSION;
            if (error instanceof InterruptedIOException || error instanceof InterruptedException) return Reason.CANCELLED;
        }
        return Reason.UNKNOWN;
    }
}
