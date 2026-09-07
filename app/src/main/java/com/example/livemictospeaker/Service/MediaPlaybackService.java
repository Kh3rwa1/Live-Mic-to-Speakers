package com.example.livemictospeaker.Service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/** Bound, foreground-screen playback. Call its methods from the main thread. */
public class MediaPlaybackService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    public static final String MPS_COMPLETED = "com.example.livemictospeaker.MediaPlaybackService.COMPLETED";
    public static final String MPS_MESSAGE = "com.example.livemictospeaker.MediaPlaybackService.MESSAGE";
    public static final String MPS_PREPARE_COMPLETED = "com.example.livemictospeaker.MediaPlaybackService.PREPARE_COMPLETED";
    public static final String MPS_RESULT = "com.example.livemictospeaker.MediaPlaybackService.RESULT";
    public static final String MPS_ERROR = "com.example.livemictospeaker.MediaPlaybackService.ERROR";
    private final IDBinder binder = new IDBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private Uri file;
    private boolean prepared, playWhenReady, hasFocus;
    private int initialPosition;
    private final AudioManager.OnAudioFocusChangeListener focus = change -> { if (change < 0) pause(); };
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            sendElapsedTime();
            if (isPlaying()) handler.postDelayed(this, 500);
        }
    };
    public class IDBinder extends Binder { public MediaPlaybackService getService() { return MediaPlaybackService.this; } }
    @Override public void onCreate() { super.onCreate(); audio = (AudioManager) getSystemService(AUDIO_SERVICE); }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_NOT_STICKY; }
    @Override public boolean onUnbind(Intent intent) { stop(); return false; }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
    public void init(Uri uri) { init(uri, 0, true); }
    public void init(Uri uri, int position, boolean autoplay) {
        stop();
        file = uri;
        initialPosition = Math.max(0, position);
        playWhenReady = autoplay;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this, uri);
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnErrorListener((mp, what, extra) -> { if (mp == player) fail(); return true; });
            player.prepareAsync();
        } catch (Exception error) { fail(); }
    }
    @Override public void onPrepared(MediaPlayer ready) {
        if (ready != player) return;
        prepared = true;
        try {
            ready.seekTo(Math.min(initialPosition, Math.max(0, ready.getDuration())));
            if (playWhenReady) play();
            broadcast(MPS_PREPARE_COMPLETED);
            sendElapsedTime();
        } catch (RuntimeException error) { fail(); }
    }
    public void play() {
        playWhenReady = true;
        if (!prepared || player == null) return;
        if (!hasFocus) hasFocus = audio != null && audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasFocus) { playWhenReady = false; broadcast(MPS_ERROR); return; }
        try { player.start(); handler.removeCallbacks(progress); handler.post(progress); }
        catch (RuntimeException error) { fail(); }
    }
    public void pause() {
        playWhenReady = false;
        if (prepared && player != null) { try { if (player.isPlaying()) player.pause(); } catch (RuntimeException ignored) {} }
        handler.removeCallbacks(progress);
        abandonFocus();
        sendElapsedTime();
    }
    public void stop() {
        handler.removeCallbacks(progress);
        if (player != null) { try { player.release(); } catch (RuntimeException ignored) {} player = null; }
        prepared = false;
        playWhenReady = false;
        file = null;
        abandonFocus();
    }
    private void abandonFocus() { if (hasFocus && audio != null) audio.abandonAudioFocus(focus); hasFocus = false; }
    public void seekTo(int position) {
        initialPosition = Math.max(0, position);
        if (prepared && player != null) { try { player.seekTo(Math.min(initialPosition, getDuration())); } catch (RuntimeException error) { fail(); } }
    }
    public int getCurrentPosition() {
        if (prepared && player != null) { try { return player.getCurrentPosition(); } catch (RuntimeException ignored) {} }
        return initialPosition;
    }
    public int getDuration() {
        if (prepared && player != null) { try { return player.getDuration(); } catch (RuntimeException ignored) {} }
        return 0;
    }
    public boolean isPlaying() {
        if (prepared && player != null) { try { return player.isPlaying(); } catch (RuntimeException ignored) {} }
        return false;
    }
    public boolean wantsPlayback() { return playWhenReady; }
    public Uri getFile() { return file; }
    @Override public void onCompletion(MediaPlayer completed) {
        if (completed != player) return;
        playWhenReady = false;
        handler.removeCallbacks(progress);
        abandonFocus();
        sendElapsedTime();
        broadcast(MPS_COMPLETED);
    }
    public void sendElapsedTime() {
        Intent update = new Intent(MPS_RESULT).putExtra(MPS_MESSAGE, getCurrentPosition());
        LocalBroadcastManager.getInstance(this).sendBroadcast(update);
    }
    private void broadcast(String action) { LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action)); }
    private void fail() { stop(); broadcast(MPS_ERROR); }
}
