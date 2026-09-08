package com.word.way.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Foreground-only AAC recorder. Only the recording worker touches platform resources. */
public final class RecordingSession implements AutoCloseable {
    private MediaRecorder recorder;
    private File file;
    private long startedAt;
    private boolean recording;
    public boolean isRecording() { return recording; }

    public static RecordingController<File> controller(Context context, File directory,
            RecordingController.Listener<File> listener) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        return new RecordingController<>(() -> new RecordingController.Recorder<File>() {
            private final RecordingSession session = new RecordingSession();
            public void start(BooleanSupplier wanted) throws IOException { session.start(app, directory, wanted); }
            public File stop(boolean keep) throws IOException { return session.stop(keep); }
            public void close() { session.close(); }
        }, runnable -> main.post(runnable), listener);
    }
    public void start(Context context, File directory) throws IOException { start(context, directory, () -> true); }
    private void start(Context context, File directory, BooleanSupplier wanted) throws IOException {
        if (recorder != null) throw new IllegalStateException("A recording is already active");
        if (!wanted.getAsBoolean()) throw new InterruptedIOException("Recording cancelled");
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new IOException("Microphone permission required");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create recordings folder");
        file = File.createTempFile("Rec_", ".pending", directory);
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            // A release/cancel while prepare() was blocking must not turn the microphone on later.
            if (!wanted.getAsBoolean()) throw new InterruptedIOException("Recording cancelled");
            recorder.start();
            startedAt = SystemClock.elapsedRealtime();
            recording = true;
        } catch (IOException | RuntimeException error) {
            release(); discard(file); file = null;
            throw new IOException("Could not start recording. Check microphone access and free storage.", error);
        }
    }
    public File stop(boolean keep) throws IOException {
        if (recorder == null) return null;
        File result = file;
        long duration = recording ? SystemClock.elapsedRealtime() - startedAt : 0;
        boolean stopped = false;
        RuntimeException failure = null;
        try { if (recording) { recorder.stop(); stopped = true; } }
        catch (RuntimeException error) { failure = error; }
        finally { release(); file = null; }
        if (!RecordingRules.keep(keep, stopped, duration, result == null ? 0 : result.length())) {
            discard(result);
            if (failure != null && keep && duration >= RecordingRules.MIN_DURATION_MS)
                throw new IOException("Recording could not be finalized; incomplete file removed.", failure);
            return null;
        }
        File published = RecordingFiles.publish(result);
        RecordingChanges.notifySaved();
        return published;
    }
    private static void discard(File file) {
        if (file != null && file.exists() && !file.delete()) android.util.Log.w("RecordingSession", "Could not remove incomplete recording");
    }
    private void release() {
        if (recorder != null) {
            try { recorder.reset(); } catch (RuntimeException ignored) { }
            try { recorder.release(); } catch (RuntimeException ignored) { }
        }
        recorder = null; recording = false;
    }
    @Override public void close() { try { stop(false); } catch (IOException ignored) { } }
}
