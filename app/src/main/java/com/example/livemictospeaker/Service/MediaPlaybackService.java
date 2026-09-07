package com.example.livemictospeaker.Service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.io.IOException;

public class MediaPlaybackService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    public static final String MPS_COMPLETED = "com.example.livemictospeaker.MediaPlaybackService.COMPLETED";
    public static final String MPS_MESSAGE = "com.example.livemictospeaker.MediaPlaybackService.MESSAGE";
    public static final String MPS_PREPARE_COMPLETED = "com.example.livemictospeaker.MediaPlaybackService.PREPARE_COMPLETED";
    public static final String MPS_RESULT = "com.example.livemictospeaker.MediaPlaybackService.RESULT";

    private LocalBroadcastManager broadcastManager;
    private Uri file;
    private final IDBinder idBinder = new IDBinder();
    private MediaPlayer mMediaPlayer = null;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Object playerLock = new Object();

    private final Runnable sendUpdates = new Runnable() {
        @Override
        public void run() {
            synchronized (playerLock) {
                if (mMediaPlayer != null && isPlaying()) {
                    sendElapsedTime();
                    progressHandler.postDelayed(this, 500);
                }
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    public class IDBinder extends Binder {
        public MediaPlaybackService getService() {
            return MediaPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.idBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stop();
        return super.onUnbind(intent);
    }

    public void init(Uri uri) {
        this.file = uri;
        stop();
        synchronized (playerLock) {
            try {
                MediaPlayer mediaPlayer = new MediaPlayer();
                this.mMediaPlayer = mediaPlayer;
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());
                this.mMediaPlayer.setDataSource(getApplicationContext(), uri);
                this.mMediaPlayer.setOnPreparedListener(this);
                this.mMediaPlayer.setOnCompletionListener(this);
                this.mMediaPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
                stop();
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        synchronized (playerLock) {
            if (mMediaPlayer != null) {
                mMediaPlayer.start();
                startProgressUpdates();
                if (broadcastManager != null) {
                    broadcastManager.sendBroadcast(new Intent(MPS_PREPARE_COMPLETED));
                }
            }
        }
    }

    public void pause() {
        synchronized (playerLock) {
            if (mMediaPlayer != null) {
                try {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                    }
                } catch (IllegalStateException ignored) {}
                stopProgressUpdates();
            }
        }
    }

    public void play() {
        synchronized (playerLock) {
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.start();
                    startProgressUpdates();
                } catch (IllegalStateException ignored) {}
            }
        }
    }

    public void stop() {
        stopProgressUpdates();
        synchronized (playerLock) {
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.reset();
                    mMediaPlayer.release();
                } catch (Exception ignored) {}
                this.mMediaPlayer = null;
                this.file = null;
            }
        }
    }

    public void seekTo(int position) {
        synchronized (playerLock) {
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.seekTo(position);
                } catch (IllegalStateException ignored) {}
            }
        }
    }

    public boolean isPlaying() {
        synchronized (playerLock) {
            if (mMediaPlayer != null) {
                try {
                    return mMediaPlayer.isPlaying();
                } catch (IllegalStateException ignored) {}
            }
            return false;
        }
    }

    public Uri getFile() {
        return this.file;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        stopProgressUpdates();
        if (broadcastManager != null) {
            broadcastManager.sendBroadcast(new Intent(MPS_COMPLETED));
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler.post(sendUpdates);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(sendUpdates);
    }

    public void sendElapsedTime() {
        synchronized (playerLock) {
            if (mMediaPlayer != null && broadcastManager != null) {
                try {
                    Intent intent = new Intent(MPS_RESULT);
                    intent.putExtra(MPS_MESSAGE, mMediaPlayer.getCurrentPosition());
                    broadcastManager.sendBroadcast(intent);
                } catch (Exception ignored) {}
            }
        }
    }
}
