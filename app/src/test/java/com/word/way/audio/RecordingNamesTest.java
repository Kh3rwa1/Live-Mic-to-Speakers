package com.word.way.audio;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingNamesTest {
    @Test public void appendsSourceExtensionWhenOmitted() throws Exception {
        File directory = Files.createTempDirectory("recording-rename-").toFile();
        File source = new File(directory, "Rec_old.m4a");
        try {
            assertEquals("My clip.m4a", RecordingNames.renamedTarget(source, "My clip").getName());
        } finally { directory.delete(); }
    }
    @Test public void doesNotDoubleExtension() throws Exception {
        File directory = Files.createTempDirectory("recording-rename-").toFile();
        File source = new File(directory, "Rec_old.m4a");
        try {
            assertEquals("My clip.m4a", RecordingNames.renamedTarget(source, "My clip.m4a").getName());
        } finally { directory.delete(); }
    }
    @Test public void neutralisesPathSeparators() throws Exception {
        File directory = Files.createTempDirectory("recording-rename-").toFile();
        File source = new File(directory, "Rec_old.m4a");
        try {
            assertEquals("a_b_c.m4a", RecordingNames.renamedTarget(source, "a/b\\c").getName());
        } finally { directory.delete(); }
    }
    @Test public void rejectsEmptyOrBlankNames() throws Exception {
        File directory = Files.createTempDirectory("recording-rename-").toFile();
        File source = new File(directory, "Rec_old.m4a");
        try {
            assertNull(RecordingNames.renamedTarget(source, null));
            assertNull(RecordingNames.renamedTarget(source, "   "));
        } finally { directory.delete(); }
    }
    @Test public void collisionAppendsNumericSuffix() throws Exception {
        File directory = Files.createTempDirectory("recording-rename-").toFile();
        File source = new File(directory, "Rec_old.m4a");
        assertTrue(new File(directory, "My clip.m4a").createNewFile());
        try {
            assertEquals("My clip (2).m4a", RecordingNames.renamedTarget(source, "My clip").getName());
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
    @Test public void missingParentDirectoryIsRejected() {
        assertNull(RecordingNames.renamedTarget(new File("Rec_old.m4a"), "name"));
    }
}
