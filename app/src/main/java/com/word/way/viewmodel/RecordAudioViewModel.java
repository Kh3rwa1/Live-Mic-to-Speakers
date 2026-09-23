package com.word.way.viewmodel;

import androidx.lifecycle.ViewModel;
import java.io.File;

/**
 * Preserves RecordAudio last saved file and timer base time across configuration changes.
 */
public class RecordAudioViewModel extends ViewModel {
    private File lastSaved;
    private long startedAt;

    public File getLastSaved() {
        return lastSaved;
    }

    public void setLastSaved(File lastSaved) {
        this.lastSaved = lastSaved;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
}
