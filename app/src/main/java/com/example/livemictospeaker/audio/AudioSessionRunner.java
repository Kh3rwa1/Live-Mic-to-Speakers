package com.example.livemictospeaker.audio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Serial, single-owner audio sessions. stop() never blocks the UI thread. */
public final class AudioSessionRunner implements AutoCloseable {
    public interface Session extends AutoCloseable {
        void start() throws Exception;
        /** Platform sessions should check cancellation again before turning capture on. */
        default void start(BooleanSupplier stillWanted) throws Exception {
            if (stillWanted.getAsBoolean()) start();
        }
        /** Must return promptly; zero means no frames are currently available. */
        int pump() throws Exception;
        @Override void close();
    }
    public interface Factory { Session create(long generation) throws Exception; }
    public interface Listener {
        void onStarted(long generation);
        void onError(long generation, Exception error);
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "LiveMicAudio");
        thread.setDaemon(true);
        return thread;
    });
    private final Factory factory;
    private final Listener listener;
    private volatile long generation;
    private volatile boolean closed;

    public AudioSessionRunner(Factory factory, Listener listener) {
        this.factory = factory;
        this.listener = listener;
    }
    public synchronized long start() {
        if (closed) throw new IllegalStateException("Audio runner is closed");
        long ticket = ++generation;
        worker.execute(() -> run(ticket));
        return ticket;
    }
    public synchronized void stop() { generation++; }
    public boolean isCurrent(long ticket) { return !closed && generation == ticket; }

    private void run(long ticket) {
        if (!isCurrent(ticket)) return;
        try (Session session = factory.create(ticket)) {
            if (!isCurrent(ticket)) return;
            session.start(() -> isCurrent(ticket));
            if (!isCurrent(ticket)) return;
            listener.onStarted(ticket);
            while (isCurrent(ticket)) {
                int frames = session.pump();
                if (frames < 0) throw new IllegalStateException("Audio device disconnected");
                if (frames == 0) Thread.sleep(4);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (isCurrent(ticket)) listener.onError(ticket, error);
        } catch (Exception error) {
            if (isCurrent(ticket)) listener.onError(ticket, error);
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        generation++;
        worker.shutdown();
    }
    /** For tests/background callers only. Never wait from an Activity callback. */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }
}
