package com.word.way.audio;

import java.io.File;
import java.util.Locale;

/** Pure naming rules for renaming a saved recording; kept off Android types so it is unit-testable. */
public final class RecordingNames {
    private static final int MAX_COLLISION_ATTEMPTS = 1000;
    private RecordingNames() { }

    /**
     * Resolves the destination file for a rename. Path separators are neutralised, the source
     * extension is preserved, and an existing file is never overwritten: a numeric suffix is added.
     *
     * @return the destination file, or {@code null} when the request cannot be honoured
     */
    public static File renamedTarget(File source, String requested) {
        if (source == null) return null;
        String sanitized = requested == null ? "" : requested.replace('/', '_').replace('\\', '_').trim();
        if (sanitized.isEmpty()) return null;
        File directory = source.getParentFile();
        if (directory == null) return null;
        String extension = "";
        int dot = source.getName().lastIndexOf('.');
        if (dot > 0) extension = source.getName().substring(dot);
        if (!extension.isEmpty() && !sanitized.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT)))
            sanitized = sanitized + extension;
        File candidate = new File(directory, sanitized);
        if (!candidate.exists()) return candidate;
        String stem = sanitized;
        if (!extension.isEmpty() && sanitized.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT)))
            stem = sanitized.substring(0, sanitized.length() - extension.length());
        for (int attempt = 2; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            File next = new File(directory, stem + " (" + attempt + ")" + extension);
            if (!next.exists()) return next;
        }
        return null;
    }
}
