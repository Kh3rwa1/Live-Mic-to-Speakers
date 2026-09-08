package com.word.way.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;
import static com.word.way.audio.LiveAudioFailure.Reason.*;

/** All audio resources belong to one worker invocation, never to an Activity. */
public final class AndroidAudioSession implements AudioSessionRunner.Session {
    public interface Meter { void update(int peakPercent, String output); }
    private final Context context;
    private final AudioManager manager;
    private final Meter meter;
    private final Runnable interrupted;
    private final AudioStopSignal signal = new AudioStopSignal();
    private final AudioManager.OnAudioFocusChangeListener focusListener;
    private AudioFocusRequest focusRequest;
    private AudioRouteGuard routeGuard;
    private AudioRecord input;
    private volatile AudioTrack output;
    private int routedOutputId;
    private final AudioRouting.OnRoutingChangedListener routing = router -> {
        if (router != output || !signal.isActive()) return;
        AudioDeviceInfo device = router.getRoutedDevice();
        if (device == null) { if (routedOutputId != 0) interrupt(); return; }
        int next = device.getId();
        if (routedOutputId != 0 && routedOutputId != next) interrupt();
        routedOutputId = next;
    };
    private AcousticEchoCanceler aec;
    private NoiseSuppressor ns;
    private short[] buffer;
    private int pending, offset;
    private long lastMeter;
    private boolean hasFocus;

    public AndroidAudioSession(Context context, Meter meter, Runnable interrupted) {
        this.context = context.getApplicationContext();
        this.manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.meter = meter;
        this.interrupted = interrupted;
        this.focusListener = change -> { if (change < 0) interrupt(); };
    }
    private void interrupt() {
        if (signal.requestStop()) interrupted.run();
    }
    private void checkWanted(BooleanSupplier stillWanted) throws InterruptedIOException {
        if (!signal.isActive() || !stillWanted.getAsBoolean())
            throw new InterruptedIOException("Microphone start cancelled or audio output changed");
    }
    @Override public void start() throws Exception { start(() -> true); }
    @Override public void start(BooleanSupplier stillWanted) throws Exception {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        checkWanted(stillWanted);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) throw new LiveAudioFailure(PERMISSION, "Microphone permission required");
        if (manager == null) throw new LiveAudioFailure(SERVICE_UNAVAILABLE, "Audio service unavailable");
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusListener, new Handler(Looper.getMainLooper())).build();
            hasFocus = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            hasFocus = manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        if (!hasFocus) throw new LiveAudioFailure(FOCUS_UNAVAILABLE, "Audio focus request denied");
        routeGuard = AudioRouteGuard.open(context, this::interrupt);
        Exception failure = null;
        for (int rate : new int[]{48000, 44100, 16000, 8000}) {
            checkWanted(stillWanted);
            try {
                int recBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int playBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (recBytes <= 0 || playBytes <= 0) continue;
                int bytes = Math.max(recBytes, playBytes);
                input = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes * 2);
                output = new AudioTrack(attributes,
                        new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                        bytes * 2, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                if (input.getState() != AudioRecord.STATE_INITIALIZED || output.getState() != AudioTrack.STATE_INITIALIZED)
                    throw new IOException("Unsupported audio configuration");
                output.addOnRoutingChangedListener(routing, new Handler(Looper.getMainLooper()));
                buffer = new short[(bytes + 1) / 2];
                break;
            } catch (SecurityException error) {
                releaseDevices();
                throw new LiveAudioFailure(PERMISSION, "Microphone access changed during preparation", error);
            } catch (Exception error) {
                failure = error;
                releaseDevices();
            }
        }
        if (buffer == null) throw new LiveAudioFailure(UNSUPPORTED_CONFIGURATION,
                "No supported microphone/output configuration", failure);
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(input.getAudioSessionId());
                if (aec != null) aec.setEnabled(true);
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(input.getAudioSessionId());
                if (ns != null) ns.setEnabled(true);
            }
        } catch (RuntimeException ignored) { /* Optional hardware processing. */ }
        // Keep cancellation checks on both sides of platform start calls.
        checkWanted(stillWanted);
        try { input.startRecording(); }
        catch (SecurityException error) { throw new LiveAudioFailure(PERMISSION, "Microphone access denied", error); }
        catch (RuntimeException error) { throw new LiveAudioFailure(MICROPHONE_UNAVAILABLE, "Microphone could not start", error); }
        checkWanted(stillWanted);
        try { output.play(); }
        catch (RuntimeException error) { throw new LiveAudioFailure(OUTPUT_FAILED, "Audio output could not start", error); }
        if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new LiveAudioFailure(MICROPHONE_UNAVAILABLE, "Microphone could not start");
    }
    @Override public int pump() throws IOException {
        if (!signal.isActive()) return 0;
        if (pending == 0) {
            int read = input.read(buffer, 0, buffer.length, AudioRecord.READ_NON_BLOCKING);
            if (read < 0) throw new LiveAudioFailure(READ_FAILED, "Microphone read failed (" + read + ")");
            if (read == 0) return 0;
            pending = read;
            offset = 0;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastMeter >= 150) {
                lastMeter = now;
                int peak = 0;
                for (int i = 0; i < read; i++) peak = Math.max(peak, Math.abs((int) buffer[i]));
                AudioDeviceInfo device = output.getRoutedDevice();
                meter.update(Math.min(100, peak * 100 / 32768),
                        device == null ? context.getString(com.word.way.R.string.library_system_output) : device.getProductName().toString());
            }
        }
        if (!signal.isActive()) return 0;
        int written = output.write(buffer, offset, pending, AudioTrack.WRITE_NON_BLOCKING);
        if (written < 0) throw new LiveAudioFailure(OUTPUT_FAILED, "Audio output failed (" + written + ")");
        offset += written;
        pending -= written;
        return written;
    }
    private void releaseDevices() {
        if (input != null) { try { input.release(); } catch (RuntimeException ignored) { } input = null; }
        if (output != null) {
            try { output.removeOnRoutingChangedListener(routing); } catch (RuntimeException ignored) { }
            try { output.release(); } catch (RuntimeException ignored) { }
            output = null;
        }
    }
    @Override public void close() {
        signal.close();
        if (routeGuard != null) {
            try { routeGuard.close(); } catch (RuntimeException ignored) { }
            routeGuard = null;
        }
        if (aec != null) { try { aec.release(); } catch (RuntimeException ignored) { } aec = null; }
        if (ns != null) { try { ns.release(); } catch (RuntimeException ignored) { } ns = null; }
        releaseDevices();
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) manager.abandonAudioFocusRequest(focusRequest);
            else manager.abandonAudioFocus(focusListener);
            hasFocus = false;
            focusRequest = null;
        }
    }
}
