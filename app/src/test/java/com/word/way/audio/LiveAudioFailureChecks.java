package com.word.way.audio;

import java.io.IOException;
import java.io.InterruptedIOException;

/** Dependency-free checks also run by JUnit in both build variants. */
public final class LiveAudioFailureChecks {
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void runAll() {
        for (LiveAudioFailure.Reason reason : LiveAudioFailure.Reason.values()) {
            LiveAudioFailure failure = new LiveAudioFailure(reason, "diagnostic only");
            equal(reason, failure.getReason());
            equal(reason, LiveAudioFailure.reasonOf(new IOException("wrapper", failure)));
        }
        equal(LiveAudioFailure.Reason.PERMISSION, LiveAudioFailure.reasonOf(new IOException(new SecurityException())));
        equal(LiveAudioFailure.Reason.CANCELLED, LiveAudioFailure.reasonOf(new InterruptedIOException()));
        equal(LiveAudioFailure.Reason.CANCELLED, LiveAudioFailure.reasonOf(new InterruptedException()));
        equal(LiveAudioFailure.Reason.UNKNOWN, LiveAudioFailure.reasonOf(null));
        equal(LiveAudioFailure.Reason.UNKNOWN, LiveAudioFailure.reasonOf(new IOException("arbitrary platform message")));
        IOException first = new IOException(), second = new IOException();
        first.initCause(second); second.initCause(first);
        equal(LiveAudioFailure.Reason.UNKNOWN, LiveAudioFailure.reasonOf(first));
        try { new LiveAudioFailure(null, "invalid"); throw new AssertionError("Null reason accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) runAll();
        System.out.println("PASS: live-audio failure classification, wrapping, cancellation and cyclic causes x 1000");
    }
}
