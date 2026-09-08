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
import java.util.function.Consumer;

/** Foreground AAC recorder. Only the controller worker touches platform resources. */
public final class RecordingSession implements AutoCloseable {
    private volatile MediaRecorder recorder;
    private volatile IOException captureError;
    private Consumer<Exception> errors = error -> { };
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
            public void setErrorListener(Consumer<Exception> errors) { session.errors = errors; }
            public int readAmplitude() throws IOException { return session.readAmplitude(); }
            public void start(BooleanSupplier wanted) throws IOException { session.start(app, directory, wanted); }
            public File stop(boolean keep) throws IOException { return session.stop(keep); }
            public void close() { session.close(); }
        }, runnable -> main.post(runnable), listener);
    }
    private int readAmplitude() throws IOException {
        if (captureError != null) throw captureError;
        return recording && recorder != null ? recorder.getMaxAmplitude() : 0;
    }
    public void start(Context context, File directory) throws IOException { start(context, directory, () -> true); }
    private void start(Context context, File directory, BooleanSupplier wanted) throws IOException {
        if (recorder != null) throw new IllegalStateException("A recording is already active");
        if (!wanted.getAsBoolean()) throw new InterruptedIOException("Recording cancelled");
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new IOException("Microphone permission required");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create recordings folder");
        file = File.createTempFile("Rec_", ".pending", directory);
        captureError = null;
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            recorder.setOnErrorListener((source, what, extra) -> {
                if (source != recorder) return; // Discard late callbacks from a released recorder.
                IOException failure = new IOException("Microphone capture failed (" + what + ", " + extra + ")");
                captureError = failure;
                errors.accept(failure);
            });
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            if (!wanted.getAsBoolean()) throw new InterruptedIOException("Recording cancelled");
            if (captureError != null) throw captureError;
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
        if (captureError != null) {
            discard(result);
            if (keep) throw captureError;
            return null;
        }
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
        MediaRecorder old = recorder;
        recorder = null;
        recording = false;
        if (old != null) {
            try { old.setOnErrorListener(null); } catch (RuntimeException ignored) { }
            try { old.reset(); } catch (RuntimeException ignored) { }
            try { old.release(); } catch (RuntimeException ignored) { }
        }
    }
    @Override public void close() { try { stop(false); } catch (IOException ignored) { } }
}
