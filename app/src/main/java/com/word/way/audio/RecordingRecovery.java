package com.word.way.audio;

import java.io.File;
import java.io.IOException;

/**
 * Recovers recordings that were finalized but left as {@code .pending} because publication failed,
 * for example when the app closed mid-save. Call from a background thread, never the UI thread.
 *
 * <p>Validation is injected so the Android caller can use {@code MediaMetadataRetriever} while JVM
 * tests use a lightweight stub. Empty files are removed; non-empty files that the validator
 * rejects are quarantined as {@code *.unrecovered} so data is never destroyed; anything that looks
 * playable is published. Actively recorded files (tracked in {@link ActiveRecordings} and within the
 * in-flight grace window) are never touched.
 */
public final class RecordingRecovery {
    /** Pending files modified within this window are treated as still in flight. */
    public static final long IN_FLIGHT_GRACE_MS = 5000L;
    public static final String UNRECOVERED_SUFFIX = ".unrecovered";
    private static final int MAX_COLLISION_ATTEMPTS = 1000;

    public interface Validator { boolean isPlayable(File file); }

    private RecordingRecovery() { }

    /**
     * @param directory  recordings folder to scan; a missing directory is a no-op
     * @param now        current time in millis, used for the in-flight grace window
     * @param validator  returns true when the file is a readable audio container
     * @return the number of files successfully published
     */
    public static int recover(File directory, long now, Validator validator) {
        return recover(directory, null, now, validator);
    }

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
                if (ActiveRecordings.isActive(file)) continue;
                if (file.lastModified() > now - IN_FLIGHT_GRACE_MS) continue;
                if (file.length() == 0) { //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    continue;
                }
                if (validator != null && !validator.isPlayable(file)) {
                    quarantine(file);
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

    /**
     * Finds an available collision-safe {@code *.unrecovered} destination name in the same folder.
     */
    public static File quarantineDestination(File pending) throws IOException {
        String name = pending.getName();
        String base = name.endsWith(".pending") ? name.substring(0, name.length() - ".pending".length()) : name;
        for (int attempt = 1; attempt <= MAX_COLLISION_ATTEMPTS; attempt++) {
            String target = attempt == 1 ? base + UNRECOVERED_SUFFIX : base + " (" + attempt + ")" + UNRECOVERED_SUFFIX;
            File candidate = new File(pending.getParentFile(), target);
            if (!candidate.exists()) return candidate;
        }
        throw new IOException("Could not allocate a quarantine filename");
    }

    /**
     * Moves a rejected non-empty recording to {@code *.unrecovered} so audio data is not lost.
     */
    public static File quarantine(File file) throws IOException {
        if (file == null || !file.exists()) return null;
        File dest = quarantineDestination(file);
        if (file.renameTo(dest)) return dest;
        copyFully(file, dest);
        if (dest.isFile() && dest.length() == file.length() && file.delete()) {
            return dest;
        }
        //noinspection ResultOfMethodCallIgnored
        dest.delete();
        throw new IOException("Failed to quarantine unrecovered recording");
    }

    private static void copyFully(File source, File dest) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        } catch (IOException copyFailed) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            throw copyFailed;
        }
    }
}
