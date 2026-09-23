package com.word.way.audio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The live session now uses a blocking pump instead of sleep polling. These checks cover the
 * runner contract around that change: stop() must return immediately, and the device read must be
 * allowed to finish without ever blocking a caller or issuing a second read after cancellation.
 */
public final class LivePumpChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void stopDuringBlockingPumpReleasesPromptly() throws Exception {
        CountDownLatch pumping = new CountDownLatch(1);
        CountDownLatch allowReturn = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger pumps = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(ticket -> new AudioSessionRunner.Session() {
            public void start() { }
            public int pump() throws Exception {
                pumps.incrementAndGet();
                pumping.countDown();
                allowReturn.await(2, TimeUnit.SECONDS); // Simulates one device buffer duration.
                return 1; // A blocking implementation returns frames, never zero.
            }
            public void close() { closes.incrementAndGet(); closed.countDown(); }
        }, new AudioSessionRunner.Listener() {
            public void onStarted(long generation) { }
            public void onError(long generation, Exception error) { }
        });
        try {
            runner.start();
            check(pumping.await(2, TimeUnit.SECONDS), "Pump never ran");
            runner.stop(); // Must return immediately; the worker is inside the blocking read.
            allowReturn.countDown();
            check(closed.await(2, TimeUnit.SECONDS), "Stopped session was not released promptly");
        } finally {
            allowReturn.countDown();
            runner.close();
            check(runner.awaitTermination(2, TimeUnit.SECONDS), "Worker leaked");
        }
        check(closes.get() == 1, "Session must be released exactly once");
        check(pumps.get() == 1, "No further device reads may run after stop");
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 500; i++) stopDuringBlockingPumpReleasesPromptly();
        System.out.println("PASS: blocking-pump stop/cancel promptness x 500");
    }
}
