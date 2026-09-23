package com.word.way.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingFilesTest {
    private static File write(File file, int... bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int value : bytes) output.write(value);
        }
        return file;
    }

    @Test public void publishesOnlyAfterFinalization() throws Exception {
        File directory = Files.createTempDirectory("recording-publication-").toFile();
        File pending = File.createTempFile("Rec_", ".pending", directory);
        File published = null;
        try {
            try (FileOutputStream output = new FileOutputStream(pending)) { output.write(new byte[]{1, 2, 3}); }
            assertFalse(pending.getName().endsWith(".m4a"));
            published = RecordingFiles.publish(pending);
            assertFalse(pending.exists()); assertTrue(published.getName().endsWith(".m4a"));
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(published.toPath()));
        } finally { pending.delete(); if (published != null) published.delete(); directory.delete(); }
    }

    @Test public void collisionAddsSuffixWithoutReplacingExistingRecording() throws Exception {
        File directory = Files.createTempDirectory("recording-publication-").toFile();
        File pending = File.createTempFile("Rec_", ".pending", directory);
        File existing = new File(pending.getParentFile(), pending.getName().replace(".pending", ".m4a"));
        File published = null;
        try {
            try (FileOutputStream output = new FileOutputStream(pending)) { output.write(new byte[]{9, 8, 7}); }
            assertTrue(existing.createNewFile());
            published = RecordingFiles.publish(pending);
            assertNotEquals("Publication must not reuse the existing name", existing.getAbsolutePath(), published.getAbsolutePath());
            assertTrue(published.getName().endsWith(" (2).m4a"));
            assertEquals("The existing recording must be untouched", 0, existing.length());
            assertArrayEquals(new byte[]{9, 8, 7}, Files.readAllBytes(published.toPath()));
            assertFalse(pending.exists());
        } finally { pending.delete(); existing.delete(); if (published != null) published.delete(); directory.delete(); }
    }

    @Test public void rejectsEmptyPendingFiles() throws Exception {
        File directory = Files.createTempDirectory("recording-publication-").toFile();
        File pending = File.createTempFile("Rec_", ".pending", directory);
        try { RecordingFiles.publish(pending); fail("Empty file published"); }
        catch (IOException expected) { assertTrue(pending.exists()); }
        finally { pending.delete(); directory.delete(); }
    }

    @Test public void newPendingFileIsReadableAndCollisionSafe() throws Exception {
        File directory = Files.createTempDirectory("recording-naming-").toFile();
        long stamp = 1_759_000_000_000L; // Fixed instant for a deterministic name.
        File first = RecordingFiles.newPendingFile(directory, stamp);
        try {
            assertTrue(first.getName().matches("Rec_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.pending"));
            assertTrue(first.createNewFile());
            File second = RecordingFiles.newPendingFile(directory, stamp);
            assertNotEquals(first.getName(), second.getName());
            assertTrue(second.getName().endsWith("_2.pending"));
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }

    @Test public void newPendingFileRejectsMissingDirectory() {
        File missing = new File("/definitely/not/a/real/path/" + System.nanoTime());
        try { RecordingFiles.newPendingFile(missing, 0L); fail("Missing directory accepted"); }
        catch (IOException expected) { }
    }

    @Test public void recoveryPublishesValidAndDeletesInvalidFiles() throws Exception {
        File directory = Files.createTempDirectory("recording-recovery-").toFile();
        long now = System.currentTimeMillis();
        File valid = write(new File(directory, "Rec_valid.pending"), 1, 2, 3);
        File empty = new File(directory, "Rec_empty.pending");
        assertTrue(empty.createNewFile());
        File invalid = write(new File(directory, "Rec_invalid.pending"), 1, 2, 3);
        for (File file : new File[]{valid, empty, invalid}) file.setLastModified(now - 60_000L);
        try {
            int published = RecordingRecovery.recover(directory, null, now,
                    file -> !file.getName().contains("invalid"));
            assertEquals(1, published);
            assertFalse("Valid pending must be published", valid.exists());
            assertTrue(new File(directory, "Rec_valid.m4a").isFile());
            assertFalse("Empty pending must be deleted", empty.exists());
            assertFalse("Unreadable pending must be deleted", invalid.exists());
            assertFalse("Invalid file must not be published", new File(directory, "Rec_invalid.m4a").exists());
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }

    @Test public void recoverySkipsInFlightAndActiveFiles() throws Exception {
        File directory = Files.createTempDirectory("recording-recovery-").toFile();
        long now = System.currentTimeMillis();
        File inFlight = write(new File(directory, "Rec_recent.pending"), 1, 2, 3);
        inFlight.setLastModified(now - 100L); // Within the in-flight grace window.
        File active = write(new File(directory, "Rec_active.pending"), 1, 2, 3);
        active.setLastModified(now - 60_000L);
        try {
            int published = RecordingRecovery.recover(directory, active, now, file -> true);
            assertEquals(0, published);
            assertTrue("In-flight capture must be left alone", inFlight.exists());
            assertTrue("The actively recording file must never be touched", active.exists());
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }

    @Test public void recoveryOnMissingDirectoryIsNoop() {
        assertEquals(0, RecordingRecovery.recover(null, null, 0L, file -> true));
        assertEquals(0, RecordingRecovery.recover(new File("/definitely/not/a/real/path/" + System.nanoTime()),
                null, 0L, file -> true));
    }
}
