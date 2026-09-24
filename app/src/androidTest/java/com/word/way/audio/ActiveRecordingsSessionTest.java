package com.word.way.audio;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;

import static org.junit.Assert.*;

/**
 * Verifies that RecordingSession reliably registers in ActiveRecordings upon starting
 * and unregisters across all exit paths: normal completion, cancellation, and failure.
 */
@RunWith(AndroidJUnit4.class)
public class ActiveRecordingsSessionTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private File directory;

    @Before
    public void setUp() {
        directory = new File(context.getCacheDir(), "active-rec-session-test-" + System.nanoTime());
        assertTrue(directory.mkdirs());
        ActiveRecordings.clear();
    }

    @After
    public void tearDown() {
        ActiveRecordings.clear();
        if (directory != null) {
            File[] files = directory.listFiles();
            if (files != null) for (File f : files) f.delete();
            directory.delete();
        }
    }

    @Test
    public void failureUnregistersFile() {
        assertEquals("ActiveRecordings must be empty initially", 0, ActiveRecordings.activeCount());
        RecordingSession session = new RecordingSession();
        File invalidDir = new File("/proc/forbidden_non_writable_dir_" + System.nanoTime());
        try {
            session.start(context, invalidDir);
            fail("Expected failure on invalid directory");
        } catch (IOException expected) {
            assertEquals("ActiveRecordings must be unregistered when start fails",
                    0, ActiveRecordings.activeCount());
        }
    }

    @Test
    public void cancelUnregistersFile() {
        assertEquals("ActiveRecordings must be empty initially", 0, ActiveRecordings.activeCount());
        RecordingSession session = new RecordingSession();
        try {
            // wanted returns false, causing InterruptedIOException inside prepare/start
            session.start(context, directory, () -> false);
            fail("Expected cancellation");
        } catch (InterruptedIOException expected) {
            assertEquals("ActiveRecordings must be unregistered on cancellation",
                    0, ActiveRecordings.activeCount());
        } catch (IOException unexpected) {
            assertEquals("ActiveRecordings must be unregistered on any start failure",
                    0, ActiveRecordings.activeCount());
        }
    }

    @Test
    public void sessionRegistersAndUnregistersOnNormalLifecycle() throws Exception {
        assertEquals("ActiveRecordings must be empty initially", 0, ActiveRecordings.activeCount());
        RecordingSession session = new RecordingSession();
        try {
            session.start(context, directory);
            assertEquals("ActiveRecordings must register the in-flight pending file",
                    1, ActiveRecordings.activeCount());
            Thread.sleep(600); // Record briefly
            File published = session.stop(true);
            assertEquals("ActiveRecordings must unregister file after stop",
                    0, ActiveRecordings.activeCount());
            if (published != null) {
                assertTrue(published.exists());
                published.delete();
            }
        } catch (IOException micUnavailable) {
            // Microphone hardware or audio permission might be unavailable in a headless environment.
            // In all cases, verify that ActiveRecordings is not left with dangling entries.
            assertEquals("ActiveRecordings must remain empty when recording cannot start",
                    0, ActiveRecordings.activeCount());
        } finally {
            session.close();
            assertEquals("ActiveRecordings must be empty after close", 0, ActiveRecordings.activeCount());
        }
    }
}
