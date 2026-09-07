package com.example.livemictospeaker.audio;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** A single worker owns preparation, finalization and release. UI calls never wait. */
public final class RecordingController<T> implements AutoCloseable {
    public enum State { IDLE, STARTING, RECORDING, STOPPING, CLOSED }
    public interface Recorder<T> extends AutoCloseable {
        void start(BooleanSupplier stillWanted) throws Exception;
        T stop(boolean keep) throws Exception;
        @Override void close();
    }
    public interface Factory<T> { Recorder<T> create() throws Exception; }
    public interface Listener<T> {
        void onStateChanged();
        void onFinished(T result, boolean requestedKeep);
        void onError(Exception error);
    }
    private final Factory<T> factory;
    private final Executor callbacks;
    private final Listener<T> listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RecordingWorker"); t.setDaemon(true); return t;
    });
    private volatile State state = State.IDLE;
    private volatile boolean wanted;
    private Recorder<T> recorder; // Worker thread only.

    public RecordingController(Factory<T> factory, Executor callbacks, Listener<T> listener) {
        this.factory = factory; this.callbacks = callbacks; this.listener = listener;
    }
    public State getState() { return state; }
    public boolean isActive() { return state == State.STARTING || state == State.RECORDING; }
    public synchronized boolean start() {
        if (state != State.IDLE) return false;
        wanted = true;
        state = State.STARTING;
        changed();
        worker.execute(() -> {
            try {
                if (!wanted) return;
                recorder = factory.create();
                if (!wanted) { release(); return; }
                recorder.start(() -> wanted);
                synchronized (this) {
                    if (state == State.STARTING) state = State.RECORDING;
                }
                changed();
            } catch (Exception error) {
                release();
                boolean report;
                synchronized (this) {
                    report = state == State.STARTING;
                    if (report) { wanted = false; state = State.IDLE; }
                }
                if (report) dispatch(() -> listener.onError(error));
                changed();
            }
        });
        return true;
    }
    public synchronized boolean stop(boolean keep) {
        if (!isActive()) return false;
        wanted = false;
        state = State.STOPPING;
        changed();
        worker.execute(() -> {
            T result = null;
            Exception failure = null;
            try { if (recorder != null) result = recorder.stop(keep); }
            catch (Exception error) { failure = error; }
            finally { release(); }
            synchronized (this) { if (state != State.CLOSED) state = State.IDLE; }
            final T saved = result;
            final Exception error = failure;
            dispatch(() -> {
                listener.onStateChanged();
                if (error != null) listener.onError(error);
                else listener.onFinished(saved, keep);
            });
        });
        return true;
    }
    private void release() {
        if (recorder != null) {
            try { recorder.close(); } catch (RuntimeException ignored) { }
            recorder = null;
        }
    }
    private void changed() { dispatch(listener::onStateChanged); }
    private void dispatch(Runnable callback) {
        callbacks.execute(() -> { if (state != State.CLOSED) callback.run(); });
    }
    @Override public synchronized void close() {
        if (state == State.CLOSED) return;
        wanted = false;
        state = State.CLOSED;
        // An already queued stop(keep=true) still finalizes the file, without touching a dead UI.
        worker.execute(this::release);
        worker.shutdown();
    }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }
}
