package com.example.livemictospeaker.audio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs unchanged under JUnit in Gradle and with plain Java for offline verification. */
public final class ReliabilityChecks {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(3, TimeUnit.SECONDS), "Timed out"); }
    private static AudioSessionRunner.Listener listener(CountDownLatch errors) {
        return new AudioSessionRunner.Listener() {
            public void onStarted(long generation) {}
            public void onError(long generation, Exception error) { errors.countDown(); }
        };
    }
    private static void shutdown(AudioSessionRunner runner) throws Exception {
        runner.close(); check(runner.awaitTermination(3, TimeUnit.SECONDS), "Worker did not stop");
    }
    public static void unusedScreen() throws Exception {
        AtomicInteger created = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(g -> { created.incrementAndGet(); return null; }, listener(new CountDownLatch(1)));
        shutdown(runner);
        check(created.get() == 0, "Opening/closing must not allocate audio");
        boolean rejected = false;
        try { runner.start(); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "Closed runner accepted a session");
    }
    public static void stopReleasesOnce() throws Exception {
        CountDownLatch started = new CountDownLatch(1), released = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(g -> new AudioSessionRunner.Session() {
            public void start() { started.countDown(); }
            public int pump() { return 0; }
            public void close() { closes.incrementAndGet(); released.countDown(); }
        }, listener(new CountDownLatch(1)));
        try { runner.start(); await(started); runner.stop(); await(released); }
        finally { shutdown(runner); }
        check(closes.get() == 1, "Session was not released exactly once");
    }
    public static void restartWaitsForCleanup() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1), closing = new CountDownLatch(1);
        CountDownLatch allowClose = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger(), active = new AtomicInteger(), maximum = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(g -> {
            int number = created.incrementAndGet();
            return new AudioSessionRunner.Session() {
                public void start() {
                    int count = active.incrementAndGet(); maximum.accumulateAndGet(count, Math::max);
                    if (number == 1) firstStarted.countDown(); else secondStarted.countDown();
                }
                public int pump() { return 0; }
                public void close() {
                    if (number == 1) { closing.countDown(); try { await(allowClose); } catch (Exception e) { throw new AssertionError(e); } }
                    active.decrementAndGet();
                }
            };
        }, listener(new CountDownLatch(1)));
        try {
            runner.start(); await(firstStarted);
            runner.stop(); runner.start(); await(closing);
            check(created.get() == 1, "New audio allocated before old cleanup finished");
            allowClose.countDown(); await(secondStarted);
        } finally { allowClose.countDown(); shutdown(runner); }
        check(maximum.get() == 1 && active.get() == 0, "Overlapping or leaked audio session");
    }
    public static void failureReleases(boolean failStart) throws Exception {
        CountDownLatch errors = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(g -> new AudioSessionRunner.Session() {
            public void start() throws Exception { if (failStart) throw new Exception("start failure"); }
            public int pump() throws Exception { throw new Exception("device disconnected"); }
            public void close() { closes.incrementAndGet(); }
        }, listener(errors));
        try { runner.start(); await(errors); } finally { shutdown(runner); }
        check(closes.get() == 1, "Failure leaked audio resources");
    }
    public static void factoryFailure() throws Exception {
        CountDownLatch errors = new CountDownLatch(1);
        AudioSessionRunner runner = new AudioSessionRunner(g -> { throw new Exception("factory failure"); }, listener(errors));
        try { runner.start(); await(errors); } finally { shutdown(runner); }
    }
    public static void cancelDuringPreparation() throws Exception {
        CountDownLatch preparing = new CountDownLatch(1), proceed = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        AudioSessionRunner runner = new AudioSessionRunner(g -> {
            preparing.countDown(); await(proceed);
            return new AudioSessionRunner.Session() {
                public void start() { started.incrementAndGet(); }
                public int pump() { return 0; }
                public void close() { closed.countDown(); }
            };
        }, listener(new CountDownLatch(1)));
        try { runner.start(); await(preparing); runner.stop(); proceed.countDown(); await(closed); }
        finally { proceed.countDown(); shutdown(runner); }
        check(started.get() == 0, "Cancelled session started recording");
    }
    public static void recordingValidity() {
        check(RecordingRules.keep(true, true, 500, 1), "Valid boundary recording discarded");
        check(!RecordingRules.keep(true, true, 499, 1), "Short recording saved");
        check(!RecordingRules.keep(true, false, 1000, 100), "Failed recording saved");
        check(!RecordingRules.keep(false, true, 1000, 100), "Cancelled recording saved");
        check(!RecordingRules.keep(true, true, 1000, 0), "Empty recording saved");
        check(!RecordingRules.keep(true, true, -1, 100), "Invalid duration accepted");
    }
    public static void main(String[] args) throws Exception {
        unusedScreen(); stopReleasesOnce(); restartWaitsForCleanup(); failureReleases(true);
        failureReleases(false); factoryFailure(); cancelDuringPreparation(); recordingValidity();
        System.out.println("PASS: 8 reliability scenarios (session lifecycle, races, errors, recording validity)");
    }
}
