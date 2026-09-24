package com.word.way.audio;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class RecordingRecoveryTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File directory;

    @Before
    public void setUp() throws IOException {
        directory = temp.newFolder("recordings");
        ActiveRecordings.clear();
    }

    @After
    public void tearDown() {
        ActiveRecordings.clear();
    }

    private static File write(File file, int... bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int value : bytes) output.write(value);
        }
        return file;
    }

    @Test
    public void nonEmptyUnreadableTwoDayOldPendingFileSurvivesSweepEmptyPending() throws Exception {
        long now = System.currentTimeMillis();
        long twoDaysAgo = now - 48L * 3600_000L;
        File pending = write(new File(directory, "Rec_old_nonempty.pending"), 1, 2, 3, 4, 5);
        pending.setLastModified(twoDaysAgo);

        int removed = RecordingFiles.sweepEmptyPending(directory, now);
        assertEquals("Non-empty pending file must not be removed by sweepEmptyPending", 0, removed);
        assertTrue("Non-empty pending file must survive the sweep", pending.isFile());
        assertEquals("File content must remain intact", 5, pending.length());
    }

    @Test
    public void emptyTwoDayOldPendingFileIsRemovedBySweep() throws Exception {
        long now = System.currentTimeMillis();
        long twoDaysAgo = now - 48L * 3600_000L;
        File emptyPending = new File(directory, "Rec_old_empty.pending");
        assertTrue(emptyPending.createNewFile());
        emptyPending.setLastModified(twoDaysAgo);

        int removed = RecordingFiles.sweepEmptyPending(directory, now);
        assertEquals("Zero-length pending file older than 24h must be removed", 1, removed);
        assertFalse("Empty pending file must be deleted by the sweep", emptyPending.exists());
    }

    @Test
    public void emptyRecentPendingFileSurvivesSweep() throws Exception {
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - 5L * 60_000L;
        File recentEmpty = new File(directory, "Rec_recent_empty.pending");
        assertTrue(recentEmpty.createNewFile());
        recentEmpty.setLastModified(fiveMinutesAgo);

        int removed = RecordingFiles.sweepEmptyPending(directory, now);
        assertEquals("Recent empty pending file must not be swept", 0, removed);
        assertTrue("Recent empty pending file must survive", recentEmpty.isFile());
    }

    @Test
    public void fileRegisteredInActiveRecordingsNeverTouchedByRecoverEvenIfMtimeIsOld() throws Exception {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3600_000L;
        File active = write(new File(directory, "Rec_active_recording.pending"), 10, 20, 30);
        active.setLastModified(oneHourAgo);

        ActiveRecordings.register(active);
        assertTrue("ActiveRecordings must report file is active", ActiveRecordings.isActive(active));

        int published = RecordingRecovery.recover(directory, null, now, file -> true);
        assertEquals("Active recording must not be recovered or published", 0, published);
        assertTrue("Active recording must not be touched or renamed", active.isFile());
        assertEquals(3, active.length());
        assertFalse("Destination .m4a must not exist", new File(directory, "Rec_active_recording.m4a").exists());

        // Once unregistered, recovery can proceed
        ActiveRecordings.unregister(active);
        published = RecordingRecovery.recover(directory, null, now, file -> true);
        assertEquals(1, published);
        assertFalse("Source pending must be gone after recovery", active.exists());
        assertTrue("Recovered .m4a must exist", new File(directory, "Rec_active_recording.m4a").isFile());
    }

    @Test
    public void nonEmptyFileRejectedByValidatorBecomesUnrecoveredAndCollisionGetsNumericSuffix() throws Exception {
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - 120_000L;
        File corrupt1 = write(new File(directory, "Rec_corrupt.pending"), 1, 2, 3);
        corrupt1.setLastModified(twoMinutesAgo);

        int published = RecordingRecovery.recover(directory, null, now, file -> false);
        assertEquals("Unplayable recording must not be published", 0, published);
        assertFalse("Pending file must no longer exist", corrupt1.exists());

        File unrecovered1 = new File(directory, "Rec_corrupt.unrecovered");
        assertTrue("Unplayable recording must become .unrecovered", unrecovered1.isFile());
        assertEquals(3, unrecovered1.length());

        // A second corrupted recording with colliding base name
        File corrupt2 = write(new File(directory, "Rec_corrupt.pending"), 4, 5);
        corrupt2.setLastModified(twoMinutesAgo);

        published = RecordingRecovery.recover(directory, null, now, file -> false);
        assertEquals(0, published);
        assertFalse("Second pending file must no longer exist", corrupt2.exists());

        File unrecovered2 = new File(directory, "Rec_corrupt (2).unrecovered");
        assertTrue("Colliding unrecovered file must receive numeric suffix (2)", unrecovered2.isFile());
        assertEquals(2, unrecovered2.length());
        assertEquals("First unrecovered file must remain untouched", 3, unrecovered1.length());
    }

    @Test
    public void validPendingFileGetsPublishedAsM4aAndCollisionGetsNumericSuffixAndSourceIsGone() throws Exception {
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - 120_000L;
        File valid1 = write(new File(directory, "Rec_valid.pending"), 1, 2, 3, 4);
        valid1.setLastModified(twoMinutesAgo);

        int published = RecordingRecovery.recover(directory, null, now, file -> true);
        assertEquals(1, published);
        assertFalse("Source pending file must be gone", valid1.exists());
        File published1 = new File(directory, "Rec_valid.m4a");
        assertTrue("Valid recording must be published as .m4a", published1.isFile());
        assertEquals(4, published1.length());

        // A second recording colliding with existing published name
        File valid2 = write(new File(directory, "Rec_valid.pending"), 5, 6);
        valid2.setLastModified(twoMinutesAgo);

        published = RecordingRecovery.recover(directory, null, now, file -> true);
        assertEquals(1, published);
        assertFalse("Second source pending file must be gone", valid2.exists());
        File published2 = new File(directory, "Rec_valid (2).m4a");
        assertTrue("Colliding published file must receive numeric suffix (2)", published2.isFile());
        assertEquals(2, published2.length());
        assertEquals("First published file must remain untouched", 4, published1.length());
    }

    @Test
    public void publishFailureKeepsPendingFile() throws Exception {
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - 120_000L;
        File readOnlyDir = temp.newFolder("readonly_recordings");
        File pending = write(new File(readOnlyDir, "Rec_readonly.pending"), 1, 2, 3);
        pending.setLastModified(twoMinutesAgo);

        if (readOnlyDir.setWritable(false) && !readOnlyDir.canWrite()) {
            try {
                int published = RecordingRecovery.recover(readOnlyDir, null, now, file -> true);
                assertEquals("Publish should fail on read-only directory", 0, published);
                assertTrue("Source pending file must be kept when publication fails", pending.isFile());
                assertEquals(3, pending.length());
            } finally {
                readOnlyDir.setWritable(true);
            }
        }
    }

    @Test
    public void zeroLengthFileIsDeletedRatherThanQuarantined() throws Exception {
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - 120_000L;
        File empty = new File(directory, "Rec_empty_zero.pending");
        assertTrue(empty.createNewFile());
        empty.setLastModified(twoMinutesAgo);

        int published = RecordingRecovery.recover(directory, null, now, file -> false);
        assertEquals(0, published);
        assertFalse("Empty pending file must be deleted", empty.exists());
        assertFalse("Empty pending file must NOT be quarantined as .unrecovered",
                new File(directory, "Rec_empty_zero.unrecovered").exists());
    }
}
