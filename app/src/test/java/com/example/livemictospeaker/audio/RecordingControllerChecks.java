package com.example.livemictospeaker.audio;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Deterministic fake-recorder checks, executable under JUnit or a plain JDK. */
public final class RecordingControllerChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(3, TimeUnit.SECONDS), "Timed out"); }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        check(condition.getAsBoolean(), "State transition timed out");
    }
    private static void close(RecordingController<?> controller) throws Exception {
        controller.close(); check(controller.awaitTermination(3, TimeUnit.SECONDS), "Worker leaked");
    }
    private static final class Events implements RecordingController.Listener<String> {
        final AtomicInteger finished = new AtomicInteger(), errors = new AtomicInteger();
        volatile String result;
        public void onStateChanged() { }
        public void onFinished(String value, boolean keep) { result = value; finished.incrementAndGet(); }
        public void onError(Exception error) { errors.incrementAndGet(); }
    }
    public static void unusedAndClosed() throws Exception {
        AtomicInteger created = new AtomicInteger();
        RecordingController<String> c = new RecordingController<>(() -> { created.incrementAndGet(); return null; }, Runnable::run, new Events());
        close(c); check(created.get() == 0 && !c.start() && !c.stop(true), "Closed/unused controller allocated a recorder");
    }
    public static void cancelDuringFactory() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger(), releases = new AtomicInteger();
        Events events = new Events();
        RecordingController<String> c = new RecordingController<>(() -> {
            entered.countDown(); await(proceed);
            return new RecordingController.Recorder<String>() {
                public void start(BooleanSupplier wanted) { starts.incrementAndGet(); }
                public String stop(boolean keep) { return "unexpected"; }
                public void close() { releases.incrementAndGet(); }
            };
        }, Runnable::run, events);
        try { c.start(); await(entered); check(c.stop(false), "Cancel rejected"); proceed.countDown(); waitFor(() -> events.finished.get() == 1); }
        finally { proceed.countDown(); close(c); }
        check(starts.get() == 0 && releases.get() == 1 && events.result == null, "Cancelled factory started or leaked");
    }
    public static void cancelDuringPreparation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger(), releases = new AtomicInteger();
        Events events = new Events();
        RecordingController<String> c = new RecordingController<>(() -> new RecordingController.Recorder<String>() {
            public void start(BooleanSupplier wanted) throws Exception {
                entered.countDown(); await(proceed);
                if (!wanted.getAsBoolean()) throw new IOException("cancelled");
                starts.incrementAndGet();
            }
            public String stop(boolean keep) { return "unexpected"; }
            public void close() { releases.incrementAndGet(); }
        }, Runnable::run, events);
        try { c.start(); await(entered); c.stop(false); proceed.countDown(); waitFor(() -> events.finished.get() == 1); }
        finally { proceed.countDown(); close(c); }
        check(starts.get() == 0 && releases.get() == 1 && events.errors.get() == 0, "Preparation cancellation failed");
    }
    public static void finalizationSerializesRestart() throws Exception {
        CountDownLatch stopping = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger creates = new AtomicInteger(), releases = new AtomicInteger(); Events events = new Events();
        RecordingController<String> c = new RecordingController<>(() -> {
            creates.incrementAndGet();
            return new RecordingController.Recorder<String>() {
                public void start(BooleanSupplier wanted) { }
                public String stop(boolean keep) throws Exception { stopping.countDown(); await(proceed); return keep ? "saved" : null; }
                public void close() { releases.incrementAndGet(); }
            };
        }, Runnable::run, events);
        try {
            check(c.start() && !c.start(), "Double start accepted");
            waitFor(() -> c.getState() == RecordingController.State.RECORDING);
            c.stop(true); await(stopping);
            check(!c.start() && !c.stop(false) && creates.get() == 1, "Restart overlapped finalization");
            proceed.countDown(); waitFor(() -> events.finished.get() == 1);
            check("saved".equals(events.result), "Valid recording lost");
            check(c.start(), "Restart rejected after cleanup"); waitFor(() -> creates.get() == 2);
        } finally { proceed.countDown(); close(c); }
        check(releases.get() == 2, "Session cleanup count wrong");
    }
    public static void startFailure() throws Exception {
        AtomicInteger releases = new AtomicInteger(); Events events = new Events();
        RecordingController<String> c = new RecordingController<>(() -> new RecordingController.Recorder<String>() {
            public void start(BooleanSupplier wanted) throws Exception { throw new IOException("prepare failure"); }
            public String stop(boolean keep) { return "unexpected"; }
            public void close() { releases.incrementAndGet(); }
        }, Runnable::run, events);
        try { c.start(); waitFor(() -> events.errors.get() == 1); check(c.getState() == RecordingController.State.IDLE, "Failure stuck busy"); }
        finally { close(c); }
        check(releases.get() == 1 && events.finished.get() == 0, "Failed start reported saved or leaked");
    }
    public static void stopFailure() throws Exception {
        AtomicInteger releases = new AtomicInteger(); Events events = new Events();
        RecordingController<String> c = new RecordingController<>(() -> new RecordingController.Recorder<String>() {
            public void start(BooleanSupplier wanted) { }
            public String stop(boolean keep) throws Exception { throw new IOException("disk full"); }
            public void close() { releases.incrementAndGet(); }
        }, Runnable::run, events);
        try { c.start(); waitFor(() -> c.getState() == RecordingController.State.RECORDING); c.stop(true); waitFor(() -> events.errors.get() == 1); }
        finally { close(c); }
        check(releases.get() == 1 && events.finished.get() == 0, "Failed stop reported success or leaked");
    }
    public static void closePreservesQueuedSaveAndSuppressesUi() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        AtomicInteger saves = new AtomicInteger(), releases = new AtomicInteger(), ui = new AtomicInteger();
        Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
        RecordingController<String> c = new RecordingController<>(() -> new RecordingController.Recorder<String>() {
            public void start(BooleanSupplier wanted) { }
            public String stop(boolean keep) throws Exception { entered.countDown(); await(proceed); if (keep) saves.incrementAndGet(); return "saved"; }
            public void close() { releases.incrementAndGet(); }
        }, callbacks::add, new RecordingController.Listener<String>() {
            public void onStateChanged() { ui.incrementAndGet(); }
            public void onFinished(String result, boolean keep) { ui.incrementAndGet(); }
            public void onError(Exception error) { ui.incrementAndGet(); }
        });
        try {
            c.start(); waitFor(() -> c.getState() == RecordingController.State.RECORDING);
            c.stop(true); await(entered); c.close(); proceed.countDown();
        } finally { proceed.countDown(); close(c); }
        for (Runnable callback; (callback = callbacks.poll()) != null;) callback.run();
        check(saves.get() == 1 && releases.get() == 1 && ui.get() == 0, "Destroy lost a queued save or updated dead UI");
    }
    public static void rapidCycles() throws Exception {
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger(); Events events = new Events();
        RecordingController<String> c = new RecordingController<>(() -> new RecordingController.Recorder<String>() {
            public void start(BooleanSupplier wanted) { maximum.accumulateAndGet(active.incrementAndGet(), Math::max); }
            public String stop(boolean keep) { return "saved"; }
            public void close() { active.decrementAndGet(); }
        }, Runnable::run, events);
        try {
            for (int i = 1; i <= 100; i++) {
                check(c.start(), "Start rejected"); waitFor(() -> c.getState() == RecordingController.State.RECORDING);
                c.stop(true); final int expected = i; waitFor(() -> events.finished.get() == expected);
            }
        } finally { close(c); }
        check(maximum.get() == 1 && active.get() == 0 && events.errors.get() == 0, "Rapid cycles overlapped or leaked");
    }
    public static void main(String[] args) throws Exception {
        unusedAndClosed(); cancelDuringFactory(); cancelDuringPreparation(); finalizationSerializesRestart();
        startFailure(); stopFailure(); closePreservesQueuedSaveAndSuppressesUi(); rapidCycles();
        System.out.println("PASS: 8 recording-controller scenarios, including 100 serialized recording cycles");
    }
}
