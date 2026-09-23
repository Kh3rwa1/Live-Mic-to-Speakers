package com.word.way.viewmodel;

import androidx.lifecycle.ViewModel;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Preserves Hold-to-Speak clip playback queue and last saved file reference across rotation.
 */
public class HoldToSpeakViewModel extends ViewModel {
    private final Queue<File> queue = new ArrayDeque<>();
    private File lastSaved;

    public Queue<File> getQueue() {
        return queue;
    }

    public void enqueue(File file) {
        if (file != null && file.isFile()) {
            queue.offer(file);
        }
    }

    public File pollNext() {
        File file;
        do {
            file = queue.poll();
        } while (file != null && !file.isFile());
        return file;
    }

    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    public File getLastSaved() {
        return lastSaved;
    }

    public void setLastSaved(File lastSaved) {
        this.lastSaved = lastSaved;
    }
}
