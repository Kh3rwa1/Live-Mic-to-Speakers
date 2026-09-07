package com.example.livemictospeaker.audio;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;

/** Foreground-only AAC/MPEG-4 recorder. Invalid/cancelled files are removed. */
public final class RecordingSession implements AutoCloseable {
    private MediaRecorder recorder;
    private File file;
    private long startedAt;
    private boolean recording;
    public boolean isRecording() { return recording; }

    public void start(Context context, File directory) throws IOException {
        if (recorder != null) throw new IllegalStateException("A recording is already active");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create recordings folder");
        file = File.createTempFile("Rec_", ".m4a", directory);
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            startedAt = SystemClock.elapsedRealtime();
            recording = true;
        } catch (IOException | RuntimeException error) {
            release();
            discard(file);
            file = null;
            throw new IOException("Could not start recording. Check microphone access and free storage.", error);
        }
    }
    /** Null means cancelled/too short. A failed stop is reported, never saved as success. */
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
        return result;
    }
    private static void discard(File file) {
        if (file != null && file.exists() && !file.delete()) android.util.Log.w("RecordingSession", "Could not remove incomplete recording");
    }
    private void release() {
        if (recorder != null) {
            try { recorder.reset(); } catch (RuntimeException ignored) {}
            try { recorder.release(); } catch (RuntimeException ignored) {}
        }
        recorder = null;
        recording = false;
    }
    @Override public void close() {
        try { stop(false); } catch (IOException ignored) {}
    }
}
