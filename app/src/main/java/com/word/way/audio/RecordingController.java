package com.word.way.audio;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One worker owns recorder calls, including level reads and failure cleanup. */
public final class RecordingController<T> implements AutoCloseable {
    public enum State { IDLE, STARTING, RECORDING, STOPPING, CLOSED }
    public interface Recorder<T> extends AutoCloseable {
        void start(BooleanSupplier stillWanted) throws Exception;
        T stop(boolean keep) throws Exception;
        default void setErrorListener(Consumer<Exception> listener) { }
        default int readAmplitude() throws Exception { return 0; }
        @Override void close();
    }
    public interface Factory<T> { Recorder<T> create() throws Exception; }
    public interface Listener<T> {
        void onStateChanged();
        void onFinished(T result, boolean requestedKeep);
        void onError(Exception error);
        default void onLevel(int percent) { }
    }
    private final Factory<T> factory;
    private final Executor callbacks;
    private final Listener<T> listener;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "RecordingWorker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile State state = State.IDLE;
    private volatile boolean wanted;
    private volatile long generation;
    private Recorder<T> recorder; // Worker thread only.
    private ScheduledFuture<?> meter; // Worker thread only.

    public RecordingController(Factory<T> factory, Executor callbacks, Listener<T> listener) {
        this.factory = factory;
        this.callbacks = callbacks;
        this.listener = listener;
    }
    public State getState() { return state; }
    public boolean isActive() { return state == State.STARTING || state == State.RECORDING; }
    private boolean isWanted(long ticket) { return wanted && generation == ticket && state != State.CLOSED; }

    public synchronized boolean start() {
        if (state != State.IDLE) return false;
        final long ticket = ++generation;
        wanted = true;
        state = State.STARTING;
        changed();
        worker.execute(() -> prepare(ticket));
        return true;
    }
    private void prepare(long ticket) {
        try {
            if (!isWanted(ticket)) return;
            recorder = factory.create();
            if (!isWanted(ticket)) { release(); return; }
            recorder.setErrorListener(error -> fail(ticket, error));
            recorder.start(() -> isWanted(ticket));
            synchronized (this) {
                if (state == State.STARTING && generation == ticket) state = State.RECORDING;
            }
            if (state == State.RECORDING && generation == ticket) {
                meter = worker.scheduleWithFixedDelay(() -> sample(ticket), 0, 100, TimeUnit.MILLISECONDS);
            }
            changed();
        } catch (Exception error) {
            release();
            boolean report;
            synchronized (this) {
                report = generation == ticket && state == State.STARTING;
                if (report) { wanted = false; state = State.IDLE; }
            }
            if (report) dispatch(() -> listener.onError(error));
            changed();
        }
    }
    private void sample(long ticket) {
        if (state != State.RECORDING || generation != ticket || recorder == null) return;
        try {
            final int percent = AudioLevels.percent(recorder.readAmplitude());
            dispatch(() -> {
                if (state == State.RECORDING && generation == ticket) listener.onLevel(percent);
            });
        } catch (Exception error) { fail(ticket, error); }
    }
    /** A platform callback only requests cleanup; it never touches recorder resources. */
    private synchronized void fail(long ticket, Exception error) {
        if (generation != ticket || !isActive()) return;
        wanted = false;
        state = State.STOPPING;
        changed();
        worker.execute(() -> {
            release();
            synchronized (this) { if (state != State.CLOSED) state = State.IDLE; }
            dispatch(() -> { listener.onStateChanged(); listener.onError(error); });
        });
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
        if (meter != null) { meter.cancel(false); meter = null; }
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
        generation++;
        // An already queued save still finalizes, but cannot update a destroyed screen.
        worker.execute(this::release);
        worker.shutdown();
    }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }
}
