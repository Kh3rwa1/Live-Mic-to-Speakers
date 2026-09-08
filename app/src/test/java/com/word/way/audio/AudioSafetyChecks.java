package com.word.way.audio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Dependency-free regression scenarios, also invoked by JUnit in Android CI. */
public final class AudioSafetyChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void oneInterruptionPerSession() {
        AudioStopSignal signal = new AudioStopSignal();
        require(signal.isActive(), "New signal must be active");
        require(signal.requestStop(), "First interruption must win");
        require(!signal.isActive(), "Interrupted session must stop pumping");
        require(!signal.requestStop(), "Duplicate interruption must be ignored");
        signal.close();
        require(!signal.requestStop(), "Closed signal cannot restart");
        AudioStopSignal cancelled = new AudioStopSignal();
        cancelled.close();
        require(!cancelled.requestStop(), "Callback queued before close must be suppressed");
    }
    public static void simultaneousInterruptionsDeliverOnce() throws Exception {
        AudioStopSignal signal = new AudioStopSignal();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        Runnable task = () -> {
            ready.countDown();
            try { require(go.await(2, TimeUnit.SECONDS), "Start gate timed out"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            if (signal.requestStop()) calls.incrementAndGet();
        };
        Thread first = new Thread(task), second = new Thread(task);
        first.start(); second.start();
        require(ready.await(2, TimeUnit.SECONDS), "Workers did not start");
        go.countDown(); first.join(2000); second.join(2000);
        require(!first.isAlive() && !second.isAlive(), "Workers did not finish");
        require(calls.get() == 1, "Concurrent route/focus changes must deliver exactly once");
    }
    public static void cancelledPreparationDoesNotStartCapture() throws Exception {
        CountDownLatch preparing = new CountDownLatch(1), continueStart = new CountDownLatch(1);
        AtomicInteger captures = new AtomicInteger(), releases = new AtomicInteger(), callbacks = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(ticket -> new AudioSessionRunner.Session() {
            public void start() { throw new AssertionError("Cancellation-aware overload required"); }
            public void start(BooleanSupplier wanted) throws Exception {
                preparing.countDown();
                require(continueStart.await(2, TimeUnit.SECONDS), "Preparation was not released");
                if (wanted.getAsBoolean()) captures.incrementAndGet();
            }
            public int pump() { return 0; }
            public void close() { releases.incrementAndGet(); }
        }, new AudioSessionRunner.Listener() {
            public void onStarted(long ticket) { callbacks.incrementAndGet(); }
            public void onError(long ticket, Exception error) { callbacks.incrementAndGet(); }
        });
        try {
            runner.start();
            require(preparing.await(2, TimeUnit.SECONDS), "Preparation did not start");
            runner.stop();
            continueStart.countDown();
        } finally {
            continueStart.countDown(); runner.close();
            require(runner.awaitTermination(2, TimeUnit.SECONDS), "Audio worker leaked");
        }
        require(captures.get() == 0, "Cancelled preparation started capture");
        require(releases.get() == 1, "Session must release once");
        require(callbacks.get() == 0, "Cancelled session delivered stale UI callbacks");
    }
    public static void main(String[] args) throws Exception {
        int iterations = args.length == 0 ? 1000 : Integer.parseInt(args[0]);
        for (int i = 0; i < iterations; i++) {
            oneInterruptionPerSession(); simultaneousInterruptionsDeliverOnce(); cancelledPreparationDoesNotStartCapture();
        }
        System.out.println("PASS: 3 audio-safety scenarios x " + iterations + " iterations");
    }
}
