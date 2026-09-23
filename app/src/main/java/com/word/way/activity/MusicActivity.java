package com.word.way.activity;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.word.way.audio.PlaybackState;
import com.word.way.R;
import com.word.way.databinding.ActivityMusicNewBinding;
import com.word.way.service.MediaPlaybackService;
import com.word.way.util.SystemBars;
import com.word.way.viewmodel.MusicPlayerViewModel;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import demo.ads.GoogleAds;

/** Playback deliberately stops in the background, and restores its prior state on return. */
public class MusicActivity extends AppCompatActivity {
    private ActivityMusicNewBinding binding;
    private MusicPlayerViewModel viewModel;
    private MediaPlaybackService service;
    private boolean isBound;
    private long lastErrorSequence = -1;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!isBound) return;
            service = ((MediaPlaybackService.IDBinder) binder).getService();
            lastErrorSequence = -1;
            Uri currentTrack = viewModel.getTrack();
            if (currentTrack != null) service.init(currentTrack, viewModel.getPosition(), viewModel.isAutoplay());
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
            viewModel.setAutoplay(false);
            Toast.makeText(MusicActivity.this, R.string.tool_play_error, Toast.LENGTH_LONG).show();
        }
        refresh();
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityMusicNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(MusicPlayerViewModel.class);
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().admobBanner(this, binding.nativeLay);

        binding.imageButtonPlayPause.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.quality_text));
        binding.textViewArtist.setText(R.string.tool_unknown_artist);
        binding.fab.setOnClickListener(v -> finish());
        binding.ivBack.setOnClickListener(v -> finish());
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        binding.imageButtonNext.setOnClickListener(v -> adjacent(1));
        binding.imageButtonPre.setOnClickListener(v -> adjacent(-1));

        String selected = null;
        if (viewModel.getTrack() != null) {
            // Already preserved in ViewModel across rotation
        } else if (state != null) {
            selected = state.getString("track");
            viewModel.setPosition(state.getInt("position"));
            viewModel.setAutoplay(state.getBoolean("autoplay", false));
        } else {
            selected = getIntent().getStringExtra("SONG_URI");
            viewModel.setAutoplay(getIntent().getBooleanExtra("AUTOPLAY", true));
        }
        if (selected != null) {
            Uri parsed = Uri.parse(selected);
            if (parsed.getScheme() == null) parsed = Uri.fromFile(new File(selected));
            viewModel.setTrack(parsed);
        }
        Uri track = viewModel.getTrack();
        if (track == null) { Toast.makeText(this, R.string.tool_no_selected, Toast.LENGTH_SHORT).show(); finish(); return; }

        String name = getIntent().getStringExtra("SONG_NAME");
        binding.textViewTitle.setText(name == null ? track.getLastPathSegment() : name);
        loadMetadata(track);
        loadPlaylist();
        binding.imageButtonPlayPause.setContentDescription(getString(viewModel.isAutoplay() ? R.string.tool_pause : R.string.tool_play));
        binding.imageButtonPlayPause.setOnClickListener(v -> {
            if (service == null) return;
            if (service.wantsPlayback()) service.pause(); else service.play();
            refresh();
        });
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
                    if (!isDestroyed() && selected.equals(viewModel.getTrack())) {
                        if (name != null && !name.isEmpty()) binding.textViewTitle.setText(name);
                        binding.textViewArtist.setText(artist == null ? getString(R.string.tool_unknown_artist) : artist);
                    }
                });
            } catch (RuntimeException ignored) {
                // Filename remains a usable fallback for invalid/missing metadata.
            } finally { try { metadata.release(); } catch (Exception ignored) {} }
        });
    }

    private void loadPlaylist() {
        Uri track = viewModel.getTrack();
        if (track == null || !"file".equals(track.getScheme())) return;
        File selected = new File(track.getPath());
        files.execute(() -> {
            File parent = selected.getParentFile();
            File[] found = parent == null ? null : parent.listFiles((dir, fileName) -> {
                String lower = fileName.toLowerCase(Locale.ROOT);
                return lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac");
            });
            if (found == null) return;
            Arrays.sort(found, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
            runOnUiThread(() -> { if (!isDestroyed()) viewModel.setPlaylist(found); });
        });
    }

    private void adjacent(int direction) {
        File[] playlist = viewModel.getPlaylist();
        Uri track = viewModel.getTrack();
        int current = -1;
        for (int i = 0; i < playlist.length; i++) if (Uri.fromFile(playlist[i]).equals(track)) current = i;
        int next = current + direction;
        if (current < 0 || next < 0 || next >= playlist.length) {
            Toast.makeText(this, R.string.tool_no_more, Toast.LENGTH_SHORT).show(); return;
        }
        Uri newTrack = Uri.fromFile(playlist[next]);
        viewModel.setTrack(newTrack);
        viewModel.setPosition(0);
        viewModel.setAutoplay(true);
        binding.textViewTitle.setText(playlist[next].getName());
        binding.textViewArtist.setText(R.string.tool_unknown_artist);
        loadMetadata(newTrack);
        binding.seekBar.setProgress(0);
        if (service != null) service.init(newTrack, 0, true);
    }

    @Override protected void onStart() {
        super.onStart();
        isBound = bindService(new Intent(this, MediaPlaybackService.class), connection, BIND_AUTO_CREATE);
        if (!isBound) Toast.makeText(this, R.string.tool_service_unavailable, Toast.LENGTH_LONG).show();
    }

    private void snapshot() {
        if (service != null) {
            viewModel.setPosition(service.getCurrentPosition());
            viewModel.setAutoplay(service.wantsPlayback());
        }
    }

    private void refresh() {
        if (service == null) return;
        snapshot();
        int position = viewModel.getPosition();
        boolean autoplay = viewModel.isAutoplay();
        binding.seekBar.setMax(service.getDuration());
        binding.seekBar.setEnabled(service.getDuration() > 0);
        binding.seekBar.setProgress(position);
        binding.textViewElapsedTime.setText(getString(R.string.tool_elapsed, formatTime(position)));
        binding.textViewDuration.setText(getString(R.string.tool_duration, formatTime(service.getDuration())));
        binding.imageButtonPlayPause.setImageResource(autoplay ? R.drawable.tool_pause : R.drawable.tool_play);
        binding.imageButtonPlayPause.setContentDescription(getString(autoplay ? R.string.tool_pause : R.string.tool_play));
        if (autoplay) {
            if (binding.lottiePlayerEqualizer != null && !binding.lottiePlayerEqualizer.isAnimating()) {
                binding.lottiePlayerEqualizer.playAnimation();
            }
        } else {
            if (binding.lottiePlayerEqualizer != null && binding.lottiePlayerEqualizer.isAnimating()) {
                binding.lottiePlayerEqualizer.pauseAnimation();
            }
        }
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
        if (isBound) { unbindService(connection); isBound = false; }
        service = null;
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        snapshot();
        Uri track = viewModel.getTrack();
        if (track != null) state.putString("track", track.toString());
        state.putInt("position", viewModel.getPosition());
        state.putBoolean("autoplay", viewModel.isAutoplay());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() { files.shutdownNow(); super.onDestroy(); }
}
