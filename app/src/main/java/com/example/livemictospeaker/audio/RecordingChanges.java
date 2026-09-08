package com.example.livemictospeaker.audio;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** App-scoped invalidation state. Observe with a LifecycleOwner, never observeForever. */
public final class RecordingChanges {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MutableLiveData<Long> REVISION = new MutableLiveData<>(0L);
    private static long revision;
    private RecordingChanges() { }

    public static LiveData<Long> revisions() { return REVISION; }

    /** Call only after a recording has been successfully finalized and published. */
    public static void notifySaved() {
        // Serialize increments and delivery even when separate recorder workers publish together.
        // LiveData automatically suppresses delivery to stopped/destroyed screens.
        MAIN.post(() -> REVISION.setValue(++revision));
    }
}
