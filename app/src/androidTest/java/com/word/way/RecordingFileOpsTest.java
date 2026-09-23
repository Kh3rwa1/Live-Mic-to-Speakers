package com.word.way;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.word.way.audio.RecordingFiles;
import com.word.way.audio.RecordingNames;
import com.word.way.audio.RecordingRecovery;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** On-device file-operation contract for recording naming, publication, recovery, rename, delete. */
@RunWith(AndroidJUnit4.class)
public class RecordingFileOpsTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final List<File> temporary = new ArrayList<>();

    private File directory() {
        File dir = new File(context.getCacheDir(), "recording-ops-" + System.nanoTime());
        assertTrue(dir.isDirectory() || dir.mkdirs());
        temporary.add(dir);
        return dir;
    }
    private File write(File file, int... bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int value : bytes) output.write(value);
        }
        return file;
    }
    @After public void clean() { for (File file : temporary) deleteTree(file); }
    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    @Test public void timestampPendingNamesAreReadableAndCollisionSafe() throws Exception {
        File dir = directory();
        File first = RecordingFiles.newPendingFile(dir, 1_759_000_000_000L);
        assertTrue(first.getName().matches("Rec_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.pending"));
        assertTrue(first.createNewFile());
        assertTrue(RecordingFiles.newPendingFile(dir, 1_759_000_000_000L).getName().endsWith("_2.pending"));
    }

    @Test public void publishCollisionKeepsExistingFile() throws Exception {
        File dir = directory();
        File pending = RecordingFiles.newPendingFile(dir, System.currentTimeMillis());
        write(pending, 1, 2, 3);
        File existing = new File(dir, pending.getName().replace(".pending", ".m4a"));
        assertTrue(existing.createNewFile());
        File published = RecordingFiles.publish(pending);
        assertEquals("Existing recording must be untouched", 0, existing.length());
        assertEquals(3, published.length());
        assertNotEquals("Publication must use a new name", existing.getName(), published.getName());
    }

    @Test public void recoveryPublishesValidAndRemovesInvalid() throws Exception {
        File dir = directory();
        long now = System.currentTimeMillis();
        File valid = write(new File(dir, "Rec_valid.pending"), 1, 2, 3);
        File invalid = write(new File(dir, "Rec_invalid.pending"), 1, 2, 3);
        File empty = new File(dir, "Rec_empty.pending");
        assertTrue(empty.createNewFile());
        for (File file : new File[]{valid, invalid, empty}) file.setLastModified(now - 60_000L);
        assertEquals(1, RecordingRecovery.recover(dir, null, now, file -> !file.getName().contains("invalid")));
        assertTrue(new File(dir, "Rec_valid.m4a").isFile());
        assertFalse("Unreadable pending must be removed", invalid.exists());
        assertFalse("Empty pending must be removed", empty.exists());
    }

    @Test public void renameAndDeleteOperateOnRealFiles() throws Exception {
        File dir = directory();
        File original = write(new File(dir, "Rec_original.m4a"), 1, 2, 3);
        File target = RecordingNames.renamedTarget(original, "Renamed clip");
        assertNotNull(target);
        assertTrue(original.renameTo(target));
        assertTrue(new File(dir, "Renamed clip.m4a").isFile());
        assertTrue(target.delete());
        assertFalse(target.exists());
    }
}
