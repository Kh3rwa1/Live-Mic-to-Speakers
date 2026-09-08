package com.word.way.Service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.word.way.audio.PlaybackState;

/** Bound, foreground-screen playback. All control methods are owned by the main thread. */
public class MediaPlaybackService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    private final IDBinder binder = new IDBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<PlaybackState> states = new MutableLiveData<>(
            new PlaybackState(PlaybackState.Status.IDLE, 0, 0, false));
    private PlaybackState.Status status = PlaybackState.Status.IDLE;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private Uri file;
    private boolean prepared, playWhenReady, hasFocus, noisyRegistered;
    private int initialPosition;
    private long errorSequence;
    private final AudioManager.OnAudioFocusChangeListener focus = change -> { if (change < 0) pause(); };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && playWhenReady) pause();
        }
    };
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            sendElapsedTime();
            if (isPlaying()) handler.postDelayed(this, 500);
        }
    };
    public class IDBinder extends Binder { public MediaPlaybackService getService() { return MediaPlaybackService.this; } }
    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        ContextCompat.registerReceiver(this, noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        noisyRegistered = true;
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_NOT_STICKY; }
    @Override public boolean onUnbind(Intent intent) { stop(); return false; }
    @Override public void onDestroy() {
        stop();
        if (noisyRegistered) { unregisterReceiver(noisy); noisyRegistered = false; }
        super.onDestroy();
    }
    public LiveData<PlaybackState> getPlaybackState() { return states; }
    public void init(Uri uri) { init(uri, 0, true); }
    public void init(Uri uri, int position, boolean autoplay) {
        stop();
        if (uri == null) { publishError(); return; }
        file = uri;
        initialPosition = Math.max(0, position);
        playWhenReady = autoplay;
        publish(PlaybackState.Status.PREPARING);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attributes());
            player.setDataSource(this, uri);
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnSeekCompleteListener(mp -> { if (mp == player) sendElapsedTime(); });
            player.setOnErrorListener((mp, what, extra) -> { if (mp == player) fail(); return true; });
            player.prepareAsync();
        } catch (Exception error) { fail(); }
    }
    private AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }
    @Override public void onPrepared(MediaPlayer ready) {
        if (ready != player) return;
        prepared = true;
        try {
            ready.seekTo(Math.min(initialPosition, Math.max(0, ready.getDuration())));
            if (playWhenReady) play(); else publish(PlaybackState.Status.PAUSED);
            sendElapsedTime();
        } catch (RuntimeException error) { fail(); }
    }
    @SuppressWarnings("deprecation")
    private boolean acquireFocus() {
        if (audio == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusRequest == null) {
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(attributes()).setWillPauseWhenDucked(true)
                            .setOnAudioFocusChangeListener(focus, handler).build();
                }
                return audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            return audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (RuntimeException unavailable) { return false; }
    }
    public void play() {
        if (player == null) { playWhenReady = false; publishError(); return; }
        playWhenReady = true;
        if (!prepared) { publish(PlaybackState.Status.PREPARING); return; }
        if (!hasFocus) hasFocus = acquireFocus();
        if (!hasFocus) { playWhenReady = false; publishError(); return; }
        try {
            player.start();
            publish(PlaybackState.Status.PLAYING);
            handler.removeCallbacks(progress); handler.post(progress);
        } catch (RuntimeException error) { fail(); }
    }
    public void pause() {
        playWhenReady = false;
        if (prepared && player != null) {
            try { if (player.isPlaying()) player.pause(); } catch (RuntimeException error) { fail(); return; }
        }
        handler.removeCallbacks(progress);
        abandonFocus();
        publish(player == null ? PlaybackState.Status.IDLE
                : prepared ? PlaybackState.Status.PAUSED : PlaybackState.Status.PREPARING);
    }
    public void stop() {
        handler.removeCallbacks(progress);
        if (player != null) { try { player.release(); } catch (RuntimeException ignored) { } player = null; }
        prepared = false; playWhenReady = false; file = null; initialPosition = 0;
        abandonFocus();
        publish(PlaybackState.Status.IDLE);
    }
    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (hasFocus && audio != null) {
            try {
                if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
                else audio.abandonAudioFocus(focus);
            } catch (RuntimeException ignored) { }
        }
        hasFocus = false;
    }
    public void seekTo(int position) {
        initialPosition = Math.max(0, position);
        if (prepared && player != null) {
            try { player.seekTo(Math.min(initialPosition, getDuration())); } catch (RuntimeException error) { fail(); }
        }
    }
    public int getCurrentPosition() {
        if (prepared && player != null) { try { return player.getCurrentPosition(); } catch (RuntimeException ignored) { } }
        return initialPosition;
    }
    public int getDuration() {
        if (prepared && player != null) { try { return player.getDuration(); } catch (RuntimeException ignored) { } }
        return 0;
    }
    public boolean isPlaying() {
        if (prepared && player != null) { try { return player.isPlaying(); } catch (RuntimeException ignored) { } }
        return false;
    }
    public boolean wantsPlayback() { return playWhenReady; }
    public Uri getFile() { return file; }
    @Override public void onCompletion(MediaPlayer completed) {
        if (completed != player) return;
        playWhenReady = false;
        handler.removeCallbacks(progress);
        abandonFocus();
        publish(PlaybackState.Status.COMPLETED);
    }
    public void sendElapsedTime() { publish(status); }
    private void publish(PlaybackState.Status value) {
        status = value;
        states.setValue(new PlaybackState(value, getCurrentPosition(), getDuration(), playWhenReady, errorSequence));
    }
    private void publishError() { errorSequence++; publish(PlaybackState.Status.ERROR); }
    private void fail() { stop(); publishError(); }
}
