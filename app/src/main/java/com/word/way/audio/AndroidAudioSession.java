package com.word.way.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import static com.word.way.audio.LiveAudioFailure.Reason.*;

/** All audio resources belong to one worker invocation, never to an Activity.
 * Meter and interruption callbacks are invoked on the audio worker; callers must marshal to UI. */
public final class AndroidAudioSession implements AudioSessionRunner.Session {
    /** Debug builds only; release logging is intentionally silent. */
    private static final String TAG = "LiveAudioSession";
    /** 16-bit mono PCM, so one sample occupies two bytes in either direction. */
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHANNELS = 1;
    private static final long UNDERRUN_LOG_INTERVAL_MS = 2000L;

    public interface Meter { void update(int peakPercent, String output); }
    private final Context context;
    private final AudioManager manager;
    private final Meter meter;
    private final Runnable interrupted;
    private final DoubleSupplier userGain;
    private final boolean debug;
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
    private LiveGainProcessor gain;
    private short[] buffer;
    private int sampleRate;
    private int nativeRate;
    private int framesPerBuffer;
    private int recordBufferBytes;
    private int trackBufferBytes;
    private long lastMeter;
    private long lastUnderrunLog;
    private boolean hasFocus;

    // Diagnostics telemetry (updated on worker at most every 500 ms)
    private boolean featureLowLatency;
    private boolean featureAudioPro;
    private String audioSourceName = "VOICE_COMMUNICATION (7)";
    private long sessionStartTime;
    private long lastDiagnosticsUpdate;
    private long totalFramesWritten;
    private long lastRawHead;
    private long headWrapCount;
    private float queueDepth10sMs = -1f;
    private int driftCorrectionsCount = 0;

    public AndroidAudioSession(Context context, Meter meter, Runnable interrupted) {
        this(context, meter, interrupted, () -> 1f);
    }
    /** {@code userGain} supplies the user's monitoring gain (0..1); it may be updated live. */
    public AndroidAudioSession(Context context, Meter meter, Runnable interrupted, DoubleSupplier userGain) {
        this.context = context.getApplicationContext();
        this.manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.meter = meter;
        this.interrupted = interrupted;
        this.userGain = userGain == null ? () -> 1f : userGain;
        this.debug = (this.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
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
        // Native output rate/burst first so the input and output run on the device's own clock.
        nativeRate = integerProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int nativeBurst = integerProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        Exception failure = null;
        for (int rate : AudioBufferSizing.candidateRates(nativeRate)) {
            checkWanted(stillWanted);
            try {
                int recMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int playMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (recMin <= 0 || playMin <= 0) continue;
                int recBytes = AudioBufferSizing.bufferBytes(recMin, nativeBurst, CHANNELS, BYTES_PER_SAMPLE);
                int playBytes = AudioBufferSizing.bufferBytes(playMin, nativeBurst, CHANNELS, BYTES_PER_SAMPLE);
                input = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recBytes);
                output = createOutput(attributes, rate, playBytes);
                if (input.getState() != AudioRecord.STATE_INITIALIZED || output.getState() != AudioTrack.STATE_INITIALIZED)
                    throw new IOException("Unsupported audio configuration");
                output.addOnRoutingChangedListener(routing, new Handler(Looper.getMainLooper()));
                sampleRate = rate;
                framesPerBuffer = nativeBurst;
                recordBufferBytes = recBytes;
                trackBufferBytes = playBytes;
                buffer = new short[AudioBufferSizing.scratchSamples(recBytes, playBytes, BYTES_PER_SAMPLE)];
                // A fresh processor per session restarts the gain ramp and clears any mute.
                gain = new LiveGainProcessor(rate);
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
        PackageManager pm = context.getPackageManager();
        featureLowLatency = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
        featureAudioPro = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO);
        audioSourceName = "VOICE_COMMUNICATION (7)";
        sessionStartTime = SystemClock.elapsedRealtime();
        lastDiagnosticsUpdate = 0L;
        totalFramesWritten = 0L;
        lastRawHead = 0L;
        headWrapCount = 0L;
        queueDepth10sMs = -1f;
        driftCorrectionsCount = 0;

        if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new LiveAudioFailure(MICROPHONE_UNAVAILABLE, "Microphone could not start");
        if (debug) {
            Log.d(TAG, "started rate=" + sampleRate + " nativeRate=" + nativeRate
                    + " framesPerBuffer=" + framesPerBuffer + " recordBuffer=" + recordBufferBytes
                    + " trackBuffer=" + trackBufferBytes + " lowLatency=" + (Build.VERSION.SDK_INT >= 26));
            updateDiagnosticsIfDue(0, "Initializing", sessionStartTime, true);
        }
    }
    /** API 26+ requests the platform low-latency output path; 24-25 keep the legacy constructor. */
    private AudioTrack createOutput(AudioAttributes attributes, int rate, int bufferBytes) {
        AudioFormat format = new AudioFormat.Builder().setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
        if (Build.VERSION.SDK_INT >= 26) {
            return new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
        }
        return new AudioTrack(attributes, format, bufferBytes, AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
    }
    private int integerProperty(String key) {
        try {
            String value = manager.getProperty(key);
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
    /**
     * Blocking read/write pump. The record device paces this loop, so the worker no longer sleeps
     * or busy-waits. One read returns within roughly one buffer duration, which bounds how long
     * {@link #close()} can wait after a stop request without ever blocking the UI thread.
     */
    @Override public int pump() throws IOException {
        if (!signal.isActive()) return 0;
        final AudioRecord source = input;
        final AudioTrack sink = output;
        if (source == null || sink == null) return 0;
        int read = source.read(buffer, 0, buffer.length);
        if (read < 0) throw new LiveAudioFailure(READ_FAILED, "Microphone read failed (" + read + ")");
        if (read == 0) return 0;
        meterIfDue(read);
        if (gain != null) {
            gain.setUserGain((float) userGain.getAsDouble());
            if (gain.process(buffer, read))
                throw new LiveAudioFailure(FEEDBACK_DETECTED, "Acoustic feedback detected");
        }
        if (!signal.isActive()) return 0;
        int written = 0;
        while (written < read && signal.isActive()) {
            int step = sink.write(buffer, written, read - written);
            if (step < 0) throw new LiveAudioFailure(OUTPUT_FAILED, "Audio output failed (" + step + ")");
            if (step == 0) break;
            written += step;
        }
        totalFramesWritten += written;
        logUnderrunsIfDue(sink);
        return written;
    }
    private void meterIfDue(int read) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMeter < 150) return;
        lastMeter = now;
        int peak = 0;
        for (int i = 0; i < read; i++) peak = Math.max(peak, Math.abs((int) buffer[i]));
        String route;
        try {
            AudioDeviceInfo device = output.getRoutedDevice();
            CharSequence product = device == null ? null : device.getProductName();
            route = (product == null || product.length() == 0)
                    ? context.getString(com.word.way.R.string.library_system_output)
                    : product.toString();
        } catch (RuntimeException ignored) {
            route = context.getString(com.word.way.R.string.library_system_output);
        }
        meter.update(Math.min(100, peak * 100 / 32768), route);
        if (debug) {
            updateDiagnosticsIfDue(read, route, now, false);
        }
    }

    private void updateDiagnosticsIfDue(int read, String route, long now, boolean force) {
        if (!force && now - lastDiagnosticsUpdate < 500) return;
        lastDiagnosticsUpdate = now;
        int underruns = 0;
        int capacityFrames = 0;
        int sizeFrames = 0;
        int rawHeadInt = 0;
        String perfMode = (Build.VERSION.SDK_INT >= 26) ? "UNKNOWN" : "N/A (pre-26)";
        final AudioTrack sink = output;
        if (sink != null) {
            try { underruns = sink.getUnderrunCount(); } catch (RuntimeException ignored) { }
            try { capacityFrames = sink.getBufferCapacityInFrames(); } catch (RuntimeException ignored) { }
            try { sizeFrames = sink.getBufferSizeInFrames(); } catch (RuntimeException ignored) { }
            try { rawHeadInt = sink.getPlaybackHeadPosition(); } catch (RuntimeException ignored) { }
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    int mode = sink.getPerformanceMode();
                    if (mode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) perfMode = "LOW_LATENCY";
                    else if (mode == AudioTrack.PERFORMANCE_MODE_NONE) perfMode = "NONE";
                    else if (mode == AudioTrack.PERFORMANCE_MODE_POWER_SAVING) perfMode = "POWER_SAVING";
                    else perfMode = String.valueOf(mode);
                } catch (RuntimeException ignored) { }
            }
        }
        long rawHead = (long) rawHeadInt & 0xFFFFFFFFL;
        if (rawHead < (lastRawHead & 0xFFFFFFFFL)) {
            headWrapCount++;
        }
        lastRawHead = rawHead;
        long unwrappedHead = (headWrapCount << 32) | rawHead;
        long queueFrames = Math.max(0L, totalFramesWritten - unwrappedHead);
        float queueDepthMs = (sampleRate > 0) ? (queueFrames * 1000f / sampleRate) : 0f;
        long uptime = Math.max(0L, now - sessionStartTime);
        if (uptime >= 10000 && queueDepth10sMs < 0f) {
            queueDepth10sMs = queueDepthMs;
        }
        boolean aecOn = false;
        try { if (aec != null) aecOn = aec.getEnabled(); } catch (RuntimeException ignored) { }
        boolean nsOn = false;
        try { if (ns != null) nsOn = ns.getEnabled(); } catch (RuntimeException ignored) { }

        AudioDiagnostics.update(new AudioDiagnostics(
                featureLowLatency, featureAudioPro,
                audioSourceName, aecOn, nsOn,
                perfMode, capacityFrames, sizeFrames, underruns,
                read, framesPerBuffer, sampleRate, nativeRate,
                queueDepthMs, queueDepth10sMs, uptime, driftCorrectionsCount,
                route, (float) userGain.getAsDouble()));
    }
    /** Debug-only latency evidence: sample rate, burst and cumulative underruns. */
    private void logUnderrunsIfDue(AudioTrack sink) {
        if (!debug) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastUnderrunLog < UNDERRUN_LOG_INTERVAL_MS) return;
        lastUnderrunLog = now;
        Log.d(TAG, "underruns=" + sink.getUnderrunCount() + " rate=" + sampleRate
                + " framesPerBuffer=" + framesPerBuffer + " trackBuffer=" + trackBufferBytes);
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
        if (debug && output != null) {
            try { Log.d(TAG, "final underruns=" + output.getUnderrunCount() + " rate=" + sampleRate); }
            catch (RuntimeException ignored) { }
        }
        releaseDevices();
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) manager.abandonAudioFocusRequest(focusRequest);
            else manager.abandonAudioFocus(focusListener);
            hasFocus = false;
            focusRequest = null;
        }
    }
}
