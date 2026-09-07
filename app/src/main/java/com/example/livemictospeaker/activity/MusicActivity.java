package com.example.livemictospeaker.activity;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import com.example.livemictospeaker.audio.PlaybackState;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Service.MediaPlaybackService;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import demo.ads.GoogleAds;

/** Playback deliberately stops in the background, and restores its prior state on return. */
public class MusicActivity extends AppCompatActivity {
    private MediaPlaybackService service;
    private boolean binding, autoplay = true;
    private Uri track;
    private int position;
    private long lastErrorSequence = -1;
    private ImageView play;
    private TextView title, duration, elapsed;
    private SeekBar seek;
    private File[] playlist = new File[0];
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!binding) return;
            service = ((MediaPlaybackService.IDBinder) binder).getService();
            lastErrorSequence = -1;
            if (track != null) service.init(track, position, autoplay);
            service.getPlaybackState().observe(MusicActivity.this, updates);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (service != null) service.getPlaybackState().removeObserver(updates);
            service = null;
        }
    };
    private final Observer<PlaybackState> updates = state -> {
        if (service == null || state == null) return;
        if (state.getStatus() == PlaybackState.Status.ERROR && state.getErrorSequence() != lastErrorSequence) {
            lastErrorSequence = state.getErrorSequence();
            autoplay = false;
            Toast.makeText(MusicActivity.this, R.string.tool_play_error, Toast.LENGTH_LONG).show();
        }
        refresh();
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_music_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        play = findViewById(R.id.imageButtonPlayPause);
        title = findViewById(R.id.textViewTitle);
        duration = findViewById(R.id.textViewDuration);
        elapsed = findViewById(R.id.textViewElapsedTime);
        seek = findViewById(R.id.seekBar);
        ((ImageView) findViewById(R.id.albumArt)).setImageResource(R.drawable.tool_play);
        ((TextView) findViewById(R.id.textViewArtist)).setText(R.string.tool_unknown_artist);
        findViewById(R.id.fab).setOnClickListener(v -> finish());
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.imageButtonNext).setOnClickListener(v -> adjacent(1));
        findViewById(R.id.imageButtonPre).setOnClickListener(v -> adjacent(-1));
        String selected = state == null ? getIntent().getStringExtra("SONG_URI") : state.getString("track");
        if (selected != null) {
            track = Uri.parse(selected);
            if (track.getScheme() == null) track = Uri.fromFile(new File(selected));
        }
        if (state != null) { position = state.getInt("position"); autoplay = state.getBoolean("autoplay", false); }
        else autoplay = getIntent().getBooleanExtra("AUTOPLAY", true);
        if (track == null) { Toast.makeText(this, R.string.tool_no_selected, Toast.LENGTH_SHORT).show(); finish(); return; }
        String name = getIntent().getStringExtra("SONG_NAME");
        title.setText(name == null ? track.getLastPathSegment() : name);
        loadMetadata(track);
        loadPlaylist();
        play.setContentDescription(getString(autoplay ? R.string.tool_pause : R.string.tool_play));
        play.setOnClickListener(v -> {
            if (service == null) return;
            if (service.wantsPlayback()) service.pause(); else service.play();
            refresh();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {}
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { if (service != null) service.seekTo(bar.getProgress()); }
        });
    }
    private void loadMetadata(Uri selected) {
        files.execute(() -> {
            android.media.MediaMetadataRetriever metadata = new android.media.MediaMetadataRetriever();
            try {
                metadata.setDataSource(getApplicationContext(), selected);
                String name = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                runOnUiThread(() -> {
                    if (!isDestroyed() && selected.equals(track)) {
                        if (name != null && !name.isEmpty()) title.setText(name);
                        ((TextView) findViewById(R.id.textViewArtist)).setText(artist == null ? getString(R.string.tool_unknown_artist) : artist);
                    }
                });
            } catch (RuntimeException ignored) {
                // Filename remains a usable fallback for invalid/missing metadata.
            } finally { try { metadata.release(); } catch (Exception ignored) {} }
        });
    }
    private void loadPlaylist() {
        if (!"file".equals(track.getScheme())) return;
        File selected = new File(track.getPath());
        files.execute(() -> {
            File parent = selected.getParentFile();
            File[] found = parent == null ? null : parent.listFiles((dir, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac");
            });
            if (found == null) return;
            Arrays.sort(found, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
            runOnUiThread(() -> { if (!isDestroyed()) playlist = found; });
        });
    }
    private void adjacent(int direction) {
        int current = -1;
        for (int i = 0; i < playlist.length; i++) if (Uri.fromFile(playlist[i]).equals(track)) current = i;
        int next = current + direction;
        if (current < 0 || next < 0 || next >= playlist.length) {
            Toast.makeText(this, R.string.tool_no_more, Toast.LENGTH_SHORT).show(); return;
        }
        track = Uri.fromFile(playlist[next]);
        position = 0;
        autoplay = true;
        title.setText(playlist[next].getName());
        ((TextView) findViewById(R.id.textViewArtist)).setText(R.string.tool_unknown_artist);
        loadMetadata(track);
        seek.setProgress(0);
        if (service != null) service.init(track, 0, true);
    }
    @Override protected void onStart() {
        super.onStart();
        binding = bindService(new Intent(this, MediaPlaybackService.class), connection, BIND_AUTO_CREATE);
        if (!binding) Toast.makeText(this, R.string.tool_service_unavailable, Toast.LENGTH_LONG).show();
    }
    private void snapshot() {
        if (service != null) { position = service.getCurrentPosition(); autoplay = service.wantsPlayback(); }
    }
    private void refresh() {
        if (service == null) return;
        snapshot();
        seek.setMax(service.getDuration());
        seek.setEnabled(service.getDuration() > 0);
        seek.setProgress(position);
        elapsed.setText(getString(R.string.tool_elapsed, formatTime(position)));
        duration.setText(getString(R.string.tool_duration, formatTime(service.getDuration())));
        play.setImageResource(autoplay ? R.drawable.tool_pause : R.drawable.tool_play);
        play.setContentDescription(getString(autoplay ? R.string.tool_pause : R.string.tool_play));
    }
    private static String formatTime(int millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
    @Override protected void onStop() {
        snapshot();
        if (service != null) {
            service.getPlaybackState().removeObserver(updates);
            // Stop sound now, rather than waiting for asynchronous service unbinding.
            // snapshot() above preserves the user's prior playback choice for return.
            service.pause();
        }
        if (binding) { unbindService(connection); binding = false; }
        service = null;
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        snapshot();
        if (track != null) state.putString("track", track.toString());
        state.putInt("position", position);
        state.putBoolean("autoplay", autoplay);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { files.shutdownNow(); super.onDestroy(); }
}
