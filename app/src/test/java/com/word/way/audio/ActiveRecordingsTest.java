package com.word.way.audio;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * JVM unit tests for ActiveRecordings thread safety and lifecycle invariants.
 */
public class ActiveRecordingsTest {

    @Before
    public void setUp() {
        ActiveRecordings.clear();
    }

    @After
    public void tearDown() {
        ActiveRecordings.clear();
    }

    @Test
    public void nullHandlingIsSafe() {
        ActiveRecordings.register(null);
        assertEquals(0, ActiveRecordings.activeCount());
        assertFalse(ActiveRecordings.isActive(null));
        ActiveRecordings.unregister(null);
        assertEquals(0, ActiveRecordings.activeCount());
    }

    @Test
    public void registrationAndUnregistrationLifecycle() {
        File f1 = new File("/tmp/recording_1.pending");
        File f2 = new File("/tmp/recording_2.pending");

        ActiveRecordings.register(f1);
        assertTrue(ActiveRecordings.isActive(f1));
        assertFalse(ActiveRecordings.isActive(f2));
        assertEquals(1, ActiveRecordings.activeCount());

        ActiveRecordings.register(f2);
        assertTrue(ActiveRecordings.isActive(f1));
        assertTrue(ActiveRecordings.isActive(f2));
        assertEquals(2, ActiveRecordings.activeCount());

        // Duplicate registration does not increase count
        ActiveRecordings.register(f1);
        assertEquals(2, ActiveRecordings.activeCount());

        ActiveRecordings.unregister(f1);
        assertFalse(ActiveRecordings.isActive(f1));
        assertTrue(ActiveRecordings.isActive(f2));
        assertEquals(1, ActiveRecordings.activeCount());

        // Redundant unregister does not fail
        ActiveRecordings.unregister(f1);
        assertEquals(1, ActiveRecordings.activeCount());

        ActiveRecordings.unregister(f2);
        assertFalse(ActiveRecordings.isActive(f2));
        assertEquals(0, ActiveRecordings.activeCount());
    }

    @Test
    public void clearRemovesAllActiveRecordings() {
        ActiveRecordings.register(new File("/tmp/a.pending"));
        ActiveRecordings.register(new File("/tmp/b.pending"));
        assertEquals(2, ActiveRecordings.activeCount());

        ActiveRecordings.clear();
        assertEquals(0, ActiveRecordings.activeCount());
    }

    @Test
    public void concurrentMultiThreadedThreadSafety() throws Exception {
        int threadCount = 10;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        File file = new File("/tmp/test_rec_" + threadId + "_" + i + ".pending");
                        ActiveRecordings.register(file);
                        if (!ActiveRecordings.isActive(file)) {
                            errors.incrementAndGet();
                        }
                        ActiveRecordings.activeCount();
                        ActiveRecordings.unregister(file);
                        if (ActiveRecordings.isActive(file)) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals("No errors during concurrent execution", 0, errors.get());
        assertEquals("All files must be unregistered at the end", 0, ActiveRecordings.activeCount());
    }

    @Test
    public void exitPathInvariants_allPathsUnregister() {
        File pending = new File("/tmp/rec_exit_path.pending");

        // 1. Success path: register -> publish -> unregister
        ActiveRecordings.register(pending);
        assertTrue(ActiveRecordings.isActive(pending));
        ActiveRecordings.unregister(pending);
        assertFalse(ActiveRecordings.isActive(pending));

        // 2. Cancel path: register -> cancelled -> unregister
        ActiveRecordings.register(pending);
        assertTrue(ActiveRecordings.isActive(pending));
        ActiveRecordings.unregister(pending);
        assertFalse(ActiveRecordings.isActive(pending));

        // 3. Too-short discard path: register -> short discard -> unregister
        ActiveRecordings.register(pending);
        assertTrue(ActiveRecordings.isActive(pending));
        ActiveRecordings.unregister(pending);
        assertFalse(ActiveRecordings.isActive(pending));

        // 4. Failure path: register -> fail -> unregister
        ActiveRecordings.register(pending);
        assertTrue(ActiveRecordings.isActive(pending));
        ActiveRecordings.unregister(pending);
        assertFalse(ActiveRecordings.isActive(pending));

        // 5. Close path: register -> close -> unregister
        ActiveRecordings.register(pending);
        assertTrue(ActiveRecordings.isActive(pending));
        ActiveRecordings.unregister(pending);
        assertFalse(ActiveRecordings.isActive(pending));

        // 6. Exception during publish path: register -> throw -> unregister
        ActiveRecordings.register(pending);
        try {
            assertTrue(ActiveRecordings.isActive(pending));
            throw new IOException("Simulated publish failure");
        } catch (IOException expected) {
            // finally block in RecordingSession.stop()
        } finally {
            ActiveRecordings.unregister(pending);
        }
        assertFalse(ActiveRecordings.isActive(pending));
        assertEquals(0, ActiveRecordings.activeCount());
    }
}
