package com.example.livemictospeaker.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;

/**
 * Stops monitoring on a noisy route or removal of an external output. Deliberately conservative:
 * even disconnecting an unused external output requires an explicit restart. Never auto-resumes.
 * Create a new guard for every session so queued callbacks from an old session stay invalid.
 */
public final class AudioRouteGuard implements AutoCloseable {
    private final Context context;
    private final AudioManager manager;
    private final Runnable interrupted;
    private final AudioStopSignal signal = new AudioStopSignal();
    private boolean receiverRegistered, callbackRegistered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) stopOnce();
        }
    };
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo device : removed) {
                if (device.isSink() && device.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        && device.getType() != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                    stopOnce();
                    return;
                }
            }
        }
    };

    private AudioRouteGuard(Context context, Runnable interrupted) {
        this.context = context.getApplicationContext();
        this.manager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.interrupted = interrupted;
    }
    public static AudioRouteGuard open(Context context, Runnable interrupted) {
        AudioRouteGuard guard = new AudioRouteGuard(context, interrupted);
        try {
            if (guard.manager == null) throw new IllegalStateException("Audio service unavailable");
            guard.manager.registerAudioDeviceCallback(guard.devices, new Handler(Looper.getMainLooper()));
            guard.callbackRegistered = true;
            ContextCompat.registerReceiver(guard.context, guard.receiver,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            guard.receiverRegistered = true;
            return guard;
        } catch (RuntimeException error) {
            guard.close();
            throw error;
        }
    }
    private void stopOnce() {
        if (signal.requestStop()) interrupted.run();
    }
    @Override public void close() {
        signal.close();
        if (receiverRegistered) {
            context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }
        if (callbackRegistered) {
            manager.unregisterAudioDeviceCallback(devices);
            callbackRegistered = false;
        }
    }
}
