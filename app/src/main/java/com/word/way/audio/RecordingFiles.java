package com.word.way.audio;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** History only sees the M4A name after the recorder has finalized it successfully. */
public final class RecordingFiles {
    private static final String PENDING_SUFFIX = ".pending";
    private static final String PUBLISHED_SUFFIX = ".m4a";
    private static final int MAX_COLLISION_ATTEMPTS = 1000;
    private RecordingFiles() { }

    /**
     * A readable, collision-safe pending name such as {@code Rec_2026-09-23_14-05-33.pending}.
     * A numeric suffix is appended when the same timestamp is already taken, so the caller never
     * truncates or reuses an existing recording.
     */
    public static File newPendingFile(File directory, long timestampMillis) throws IOException {
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs()))
            throw new IOException("Recordings folder unavailable");
        String base = "Rec_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date(timestampMillis));
        for (int attempt = 1; attempt <= MAX_COLLISION_ATTEMPTS; attempt++) {
            String name = attempt == 1 ? base + PENDING_SUFFIX : base + "_" + attempt + PENDING_SUFFIX;
            File candidate = new File(directory, name);
            if (!candidate.exists()) return candidate;
        }
        throw new IOException("Could not allocate a recording filename");
    }

    /**
     * Renames a finalized {@code .pending} file to {@code .m4a}. Existing recordings are never
     * overwritten: on a name collision the destination gets a numeric suffix.
     */
    public static File publish(File pending) throws IOException {
        if (pending == null || !pending.isFile() || !pending.getName().endsWith(PENDING_SUFFIX) || pending.length() == 0)
            throw new IOException("Recording is not ready to save");
        File published = availableDestination(pending);
        if (pending.renameTo(published)) return published;
        // Cross-volume rename fallback: stream copy then delete, never overwriting (API 24-safe).
        try {
            copyFully(pending, published);
            if (published.isFile() && published.length() == pending.length() && pending.delete()) return published;
            //noinspection ResultOfMethodCallIgnored
            published.delete();
        } catch (IOException | RuntimeException copyFailed) {
            // Fall through to the retained-pending error below.
        }
        throw new IOException("Recording finished but could not be published. Its temporary file was retained for recovery.");
    }

    private static File availableDestination(File pending) throws IOException {
        String name = pending.getName();
        String base = name.substring(0, name.length() - PENDING_SUFFIX.length());
        for (int attempt = 1; attempt <= MAX_COLLISION_ATTEMPTS; attempt++) {
            String target = attempt == 1 ? base + PUBLISHED_SUFFIX : base + " (" + attempt + ")" + PUBLISHED_SUFFIX;
            File candidate = new File(pending.getParentFile(), target);
            if (!candidate.exists()) return candidate;
        }
        throw new IOException("Recording finished but could not be published. Its temporary file was retained for recovery.");
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
    /** Removes stale .pending files older than a day; call on app start. Returns removed count. */
    public static int sweepStalePending(File directory) {
        if (directory == null || !directory.isDirectory()) return 0;
        File[] stale = directory.listFiles((dir, n) -> n.endsWith(PENDING_SUFFIX));
        if (stale == null) return 0;
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        int removed = 0;
        for (File file : stale) {
            try {
                if (file.lastModified() < cutoff && file.delete()) removed++;
            } catch (SecurityException ignored) { }
        }
        return removed;
    }
}
