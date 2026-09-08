package com.word.way.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Real controller, fake platform recorder: executable with JUnit or an offline JDK. */
public final class RecordingRuntimeChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        check(condition.getAsBoolean(), "Timed out waiting for state");
    }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(3, TimeUnit.SECONDS), "Latch timed out"); }
    private static void close(RecordingController<?> c) throws Exception {
        c.close(); check(c.awaitTermination(3, TimeUnit.SECONDS), "Recorder worker leaked");
    }
    private static final class Events implements RecordingController.Listener<String> {
        final AtomicInteger errors = new AtomicInteger(), finished = new AtomicInteger(), levels = new AtomicInteger();
        volatile int latest;
        public void onStateChanged() { }
        public void onFinished(String result, boolean keep) { finished.incrementAndGet(); }
        public void onError(Exception error) { errors.incrementAndGet(); }
        public void onLevel(int percent) { latest = percent; levels.incrementAndGet(); }
    }
    private static class Fake implements RecordingController.Recorder<String> {
        final AtomicInteger released = new AtomicInteger();
        volatile Consumer<Exception> errors;
        public void setErrorListener(Consumer<Exception> listener) { errors = listener; }
        public void start(BooleanSupplier wanted) throws Exception { }
        public int readAmplitude() throws Exception { return 16384; }
        public String stop(boolean keep) throws Exception { return keep ? "saved" : null; }
        public void close() {
            check(Thread.currentThread().getName().equals("RecordingWorker"), "Cleanup escaped the owner worker");
            released.incrementAndGet();
        }
    }
    public static void levelBounds() {
        check(AudioLevels.percent(-1) == 0 && AudioLevels.percent(0) == 0, "Negative/silent level");
        check(AudioLevels.percent(16384) == 50, "Half-scale level");
        check(AudioLevels.percent(32767) == 100 && AudioLevels.percent(Integer.MAX_VALUE) == 100, "Full-scale/overflow level");
    }
    public static void runtimeFailureDeliveredOnce() throws Exception {
        Fake fake = new Fake(); Events e = new Events();
        RecordingController<String> c = new RecordingController<>(() -> fake, Runnable::run, e);
        try {
            c.start(); waitFor(() -> e.levels.get() > 0);
            check(e.latest == 50, "Meter is not based on sampled amplitude");
            fake.errors.accept(new IOException("device failure"));
            fake.errors.accept(new IOException("duplicate callback"));
            waitFor(() -> e.errors.get() == 1);
            check(c.getState() == RecordingController.State.IDLE, "Runtime failure left recording active");
            check(fake.released.get() == 1 && e.finished.get() == 0, "Failed capture was saved or leaked");
        } finally { close(c); }
    }
    public static void staleFailureCannotStopRestart() throws Exception {
        Queue<Fake> sessions = new ConcurrentLinkedQueue<>(); Events e = new Events();
        RecordingController<String> c = new RecordingController<>(() -> {
            Fake f = new Fake(); sessions.add(f); return f;
        }, Runnable::run, e);
        try {
            c.start(); waitFor(() -> c.getState() == RecordingController.State.RECORDING);
            Fake first = sessions.peek(); c.stop(true); waitFor(() -> e.finished.get() == 1);
            c.start(); waitFor(() -> sessions.size() == 2 && c.getState() == RecordingController.State.RECORDING);
            first.errors.accept(new IOException("late callback from prior session"));
            check(c.getState() == RecordingController.State.RECORDING && e.errors.get() == 0, "Stale error stopped a new session");
        } finally { close(c); }
        for (Fake f : sessions) check(f.released.get() == 1, "Session released more than once");
    }
    public static void failureDuringPreparationCancelsCapture() throws Exception {
        CountDownLatch preparing = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger(); Events e = new Events();
        Fake fake = new Fake() {
            @Override public void start(BooleanSupplier wanted) throws Exception {
                preparing.countDown(); await(proceed);
                if (!wanted.getAsBoolean()) throw new IOException("cancelled");
                starts.incrementAndGet();
            }
        };
        RecordingController<String> c = new RecordingController<>(() -> fake, Runnable::run, e);
        try {
            c.start(); await(preparing); fake.errors.accept(new IOException("prepare error event"));
            proceed.countDown(); waitFor(() -> e.errors.get() == 1);
            check(starts.get() == 0 && fake.released.get() == 1 && e.finished.get() == 0, "Preparation failure leaked or started capture");
        } finally { proceed.countDown(); close(c); }
    }
    public static void meterFailureStopsSession() throws Exception {
        Events e = new Events(); Fake fake = new Fake() {
            @Override public int readAmplitude() throws Exception { throw new IOException("device lost"); }
        };
        RecordingController<String> c = new RecordingController<>(() -> fake, Runnable::run, e);
        try {
            c.start(); waitFor(() -> e.errors.get() == 1);
            check(c.getState() == RecordingController.State.IDLE && fake.released.get() == 1, "Meter failure did not clean up");
        } finally { close(c); }
    }
    public static void lateLevelsAndClosedUiAreSuppressed() throws Exception {
        Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>(); CountDownLatch sampled = new CountDownLatch(1);
        Events e = new Events(); Fake fake = new Fake() {
            @Override public int readAmplitude() { sampled.countDown(); return 32767; }
        };
        RecordingController<String> c = new RecordingController<>(() -> fake, callbacks::add, e);
        try {
            c.start(); await(sampled); c.stop(false); waitFor(() -> c.getState() == RecordingController.State.IDLE);
            for (Runnable r; (r = callbacks.poll()) != null;) r.run();
            check(e.levels.get() == 0, "Queued meter updated an idle screen");
            close(c); fake.errors.accept(new IOException("after close"));
            for (Runnable r; (r = callbacks.poll()) != null;) r.run();
            check(e.errors.get() == 0 && fake.released.get() == 1, "Closed recorder delivered errors or leaked");
        } finally { close(c); }
    }
    public static void queuedSaveSurvivesClose() throws Exception {
        CountDownLatch stopping = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger saved = new AtomicInteger(); Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>(); Events e = new Events();
        Fake fake = new Fake() {
            @Override public String stop(boolean keep) throws Exception {
                stopping.countDown(); await(proceed); if (keep) saved.incrementAndGet(); return "saved";
            }
        };
        RecordingController<String> c = new RecordingController<>(() -> fake, callbacks::add, e);
        try {
            c.start(); waitFor(() -> c.getState() == RecordingController.State.RECORDING);
            c.stop(true); await(stopping); c.close(); proceed.countDown(); close(c);
            for (Runnable r; (r = callbacks.poll()) != null;) r.run();
            check(saved.get() == 1 && e.finished.get() == 0 && e.errors.get() == 0 && fake.released.get() == 1,
                    "Closing lost a queued save or updated dead UI");
        } finally { proceed.countDown(); close(c); }
    }
    public static void cancellationAndRapidRestarts() throws Exception {
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger(); Events e = new Events();
        RecordingController<String> c = new RecordingController<>(() -> new Fake() {
            boolean started;
            @Override public void start(BooleanSupplier wanted) {
                if (wanted.getAsBoolean()) { started = true; maximum.accumulateAndGet(active.incrementAndGet(), Math::max); }
            }
            @Override public void close() { if (started) active.decrementAndGet(); super.close(); }
        }, Runnable::run, e);
        try {
            for (int i = 1; i <= 50; i++) {
                check(c.start(), "Restart rejected"); waitFor(() -> c.getState() == RecordingController.State.RECORDING);
                check(!c.start(), "Overlapping start accepted"); c.stop(false);
                final int expected = i; waitFor(() -> e.finished.get() == expected);
            }
            check(maximum.get() == 1 && active.get() == 0 && e.errors.get() == 0, "Rapid restarts overlapped");
        } finally { close(c); }
    }
    public static void runAll() throws Exception {
        levelBounds(); runtimeFailureDeliveredOnce(); staleFailureCannotStopRestart();
        failureDuringPreparationCancelsCapture(); meterFailureStopsSession();
        lateLevelsAndClosedUiAreSuppressed(); queuedSaveSurvivesClose(); cancellationAndRapidRestarts();
    }
    public static void main(String[] args) throws Exception {
        int iterations = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        for (int i = 0; i < iterations; i++) runAll();
        System.out.println("PASS: 8 runtime scenarios x " + iterations + " iterations (50 serialized restarts per iteration)");
    }
}
