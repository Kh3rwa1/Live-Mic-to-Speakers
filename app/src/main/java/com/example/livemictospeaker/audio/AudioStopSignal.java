package com.example.livemictospeaker.audio;

import java.util.concurrent.atomic.AtomicInteger;

/** One interruption per session; closed sessions cannot deliver a new interruption. */
public final class AudioStopSignal implements AutoCloseable {
    private static final int ACTIVE = 0, STOPPED = 1, CLOSED = 2;
    private final AtomicInteger state = new AtomicInteger(ACTIVE);

    public boolean requestStop() { return state.compareAndSet(ACTIVE, STOPPED); }
    public boolean isActive() { return state.get() == ACTIVE; }
    @Override public void close() { state.set(CLOSED); }
}
