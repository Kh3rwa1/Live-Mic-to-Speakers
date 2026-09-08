package com.word.way.audio;

import java.io.File;
import java.io.IOException;

/** History only sees the M4A name after the recorder has finalized it successfully. */
public final class RecordingFiles {
    private RecordingFiles() { }
    public static File publish(File pending) throws IOException {
        if (pending == null || !pending.isFile() || !pending.getName().endsWith(".pending") || pending.length() == 0)
            throw new IOException("Recording is not ready to save");
        String name = pending.getName();
        File published = new File(pending.getParentFile(), name.substring(0, name.length() - ".pending".length()) + ".m4a");
        if (published.exists() || !pending.renameTo(published))
            throw new IOException("Recording finished but could not be published. Its temporary file was retained for recovery.");
        return published;
    }
}
