package com.word.way.audio;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** A queued UI must finish with the error, not a later idle-state repaint. */
public final class StartFailureFeedbackChecks {
    public static void check() throws Exception {
        Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
        AtomicInteger posted = new AtomicInteger(), releases = new AtomicInteger();
        AtomicReference<String> lastEvent = new AtomicReference<>();
        RecordingController<String> controller = new RecordingController<>(() -> new RecordingController.Recorder<String>() {
            public void start(BooleanSupplier wanted) throws IOException { throw new IOException("storage unavailable"); }
            public String stop(boolean keep) { throw new AssertionError("Failed start cannot save"); }
            public void close() { releases.incrementAndGet(); }
        }, callback -> { callbacks.add(callback); posted.incrementAndGet(); }, new RecordingController.Listener<String>() {
            public void onStateChanged() { lastEvent.set("state"); }
            public void onFinished(String result, boolean keep) { throw new AssertionError("Failed start reported success"); }
            public void onError(Exception error) { lastEvent.set("error"); }
        });
        try {
            controller.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (posted.get() < 3 && System.nanoTime() < deadline) Thread.sleep(1);
            if (posted.get() < 3) throw new AssertionError("Callbacks did not arrive");
            for (Runnable callback; (callback = callbacks.poll()) != null;) callback.run();
            if (!"error".equals(lastEvent.get())) throw new AssertionError("Idle-state callback hid the recovery message");
            if (controller.getState() != RecordingController.State.IDLE || releases.get() != 1)
                throw new AssertionError("Start failure did not release and reset");
        } finally {
            controller.close();
            if (!controller.awaitTermination(3, TimeUnit.SECONDS)) throw new AssertionError("Worker leaked");
        }
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 100; i++) check();
        System.out.println("PASS: 100 queued start-failure feedback checks");
    }
}
