package com.word.way.audio;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide thread-safe registry of in-flight recording files.
 * Ensures recovery sweeps and background scans never touch actively recorded files.
 */
public final class ActiveRecordings {
    private static final Set<String> ACTIVE_PATHS = ConcurrentHashMap.newKeySet();

    private ActiveRecordings() { }

    public static void register(File file) {
        if (file != null) {
            ACTIVE_PATHS.add(file.getAbsolutePath());
        }
    }

    public static void unregister(File file) {
        if (file != null) {
            ACTIVE_PATHS.remove(file.getAbsolutePath());
        }
    }

    public static boolean isActive(File file) {
        if (file == null) return false;
        return ACTIVE_PATHS.contains(file.getAbsolutePath());
    }

    public static int activeCount() {
        return ACTIVE_PATHS.size();
    }

    /** Visible for testing. */
    public static void clear() {
        ACTIVE_PATHS.clear();
    }
}
