package com.word.way.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;

/** Main-thread, foreground-only preview with focus, unplug and stale-callback protection. */
public final class PreviewPlayer implements AutoCloseable {
    public interface Listener {
        void onChanged();
        void onCompleted();
        void onError();
    }
    private final Context context;
    private final AudioManager manager;
    private final Listener listener;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioManager.OnAudioFocusChangeListener focus = change -> { if (change < 0) pause(); };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pause(); }
    };
    private AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private boolean prepared, wanted, hasFocus, registered;
    private int pendingPosition;
    public PreviewPlayer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        manager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
    }
    public boolean wantsPlayback() { return wanted; }
    public boolean hasTrack() { return player != null; }
    public void play(Uri uri) {
        stop();
        MediaPlayer next = new MediaPlayer();
        player = next;
        wanted = true;
        try {
            next.setAudioAttributes(attributes);
            next.setDataSource(context, uri);
            next.setOnPreparedListener(mp -> {
                if (mp != player) return;
                prepared = true;
                seekTo(pendingPosition);
                if (wanted) resume(); else listener.onChanged();
            });
            next.setOnCompletionListener(mp -> {
                if (mp != player) return;
                wanted = false; abandonFocus(); listener.onChanged(); listener.onCompleted();
            });
            next.setOnErrorListener((mp, what, extra) -> { if (mp == player) fail(new RuntimeException("Preview error " + what + "," + extra)); return true; });
            next.prepareAsync();
            listener.onChanged();
        } catch (Exception error) { fail(error); }
    }
    public void resume() {
        if (player == null) return;
        wanted = true;
        if (!prepared) { listener.onChanged(); return; }
        try {
            if (!requestFocus()) { fail(null); return; }
            if (!registered) {
                ContextCompat.registerReceiver(context, noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                        ContextCompat.RECEIVER_NOT_EXPORTED);
                registered = true;
            }
            if (getDuration() > 0 && getPosition() >= getDuration()) player.seekTo(0);
            player.start();
            listener.onChanged();
        } catch (RuntimeException error) { fail(error); }
    }
    private boolean requestFocus() {
        if (hasFocus) return true;
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focus, new Handler(Looper.getMainLooper())).build();
            hasFocus = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            hasFocus = manager.requestAudioFocus(focus, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return hasFocus;
    }
    public void pause() {
        wanted = false;
        if (prepared && player != null) {
            try { if (player.isPlaying()) player.pause(); } catch (RuntimeException ignored) { }
        }
        abandonFocus(); listener.onChanged();
    }
    private void abandonFocus() {
        if (registered) {
            try { context.unregisterReceiver(noisy); } catch (RuntimeException ignored) { }
            registered = false;
        }
        if (hasFocus && manager != null) {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) manager.abandonAudioFocusRequest(focusRequest);
            else manager.abandonAudioFocus(focus);
        }
        hasFocus = false; focusRequest = null;
    }
    public void seekTo(int position) {
        pendingPosition = Math.max(0, position);
        if (prepared && player != null) {
            try { player.seekTo(Math.min(pendingPosition, getDuration())); } catch (RuntimeException error) { fail(error); }
        }
    }
    public int getPosition() {
        try { return prepared && player != null ? player.getCurrentPosition() : pendingPosition; }
        catch (RuntimeException ignored) { return pendingPosition; }
    }
    public int getDuration() {
        try { return prepared && player != null ? player.getDuration() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }
    public void stop() {
        MediaPlayer old = player;
        player = null; prepared = false; wanted = false; pendingPosition = 0;
        if (old != null) { try { old.release(); } catch (RuntimeException ignored) { } }
        try { abandonFocus(); } catch (RuntimeException ignored) { }
        listener.onChanged();
    }
    private void fail() { fail(null); }
    private void fail(Exception cause) {
        if (cause != null) android.util.Log.w("PreviewPlayer", "Preview failed", cause);
        stop(); listener.onError();
    }
    @Override public void close() { stop(); }
}
