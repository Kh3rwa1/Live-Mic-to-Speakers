package com.word.way.audio;

import java.io.File;
import java.io.IOException;

/**
 * Recovers recordings that were finalized but left as {@code .pending} because publication failed,
 * for example when the app closed mid-save. Call from a background thread, never the UI thread.
 *
 * <p>Validation is injected so the Android caller can use {@code MediaMetadataRetriever} while JVM
 * tests use a lightweight stub. Empty or unreadable files are deleted; anything that looks playable
 * is published. A file that is still being written is skipped using a short in-flight grace window,
 * and the actively recording file is never touched.
 */
public final class RecordingRecovery {
    /** Pending files modified within this window are treated as still in flight. */
    public static final long IN_FLIGHT_GRACE_MS = 5000L;

    public interface Validator { boolean isPlayable(File file); }

    private RecordingRecovery() { }

    /**
     * @param directory  recordings folder to scan; a missing directory is a no-op
     * @param activeFile pending file currently being recorded, or {@code null}
     * @param now        current time in millis, used for the in-flight grace window
     * @param validator  returns true when the file is a readable audio container
     * @return the number of files successfully published
     */
    public static int recover(File directory, File activeFile, long now, Validator validator) {
        if (directory == null || !directory.isDirectory()) return 0;
        File[] pendingFiles = directory.listFiles((dir, name) -> name.endsWith(".pending"));
        if (pendingFiles == null) return 0;
        String activePath = activeFile == null ? null : activeFile.getAbsolutePath();
        int published = 0;
        for (File file : pendingFiles) {
            try {
                if (activePath != null && activePath.equals(file.getAbsolutePath())) continue;
                if (file.lastModified() > now - IN_FLIGHT_GRACE_MS) continue;
                if (file.length() == 0) { //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    continue;
                }
                if (validator != null && !validator.isPlayable(file)) { //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    continue;
                }
                RecordingFiles.publish(file);
                published++;
            } catch (RuntimeException | IOException ignored) {
                // Keep anything that cannot be published or examined; never destroy unknown data.
            }
        }
        return published;
    }
}
