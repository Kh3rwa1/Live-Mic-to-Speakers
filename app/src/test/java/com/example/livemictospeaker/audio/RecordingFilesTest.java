package com.example.livemictospeaker.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingFilesTest {
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
    @Test public void neverReplacesAnExistingRecording() throws Exception {
        File directory = Files.createTempDirectory("recording-publication-").toFile();
        File pending = File.createTempFile("Rec_", ".pending", directory);
        File destination = new File(pending.getParentFile(), pending.getName().replace(".pending", ".m4a"));
        try {
            try (FileOutputStream output = new FileOutputStream(pending)) { output.write(1); }
            assertTrue(destination.createNewFile());
            try { RecordingFiles.publish(pending); fail("Existing recording replaced"); } catch (IOException expected) { }
            assertTrue(pending.isFile()); assertEquals(0, destination.length());
        } finally { pending.delete(); destination.delete(); directory.delete(); }
    }
    @Test public void rejectsEmptyPendingFiles() throws Exception {
        File directory = Files.createTempDirectory("recording-publication-").toFile();
        File pending = File.createTempFile("Rec_", ".pending", directory);
        try { RecordingFiles.publish(pending); fail("Empty file published"); }
        catch (IOException expected) { assertTrue(pending.exists()); }
        finally { pending.delete(); directory.delete(); }
    }
}
