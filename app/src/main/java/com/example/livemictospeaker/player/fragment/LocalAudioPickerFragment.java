package com.example.livemictospeaker.player.fragment;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.activity.MusicActivity;
import com.example.livemictospeaker.audio.PreviewPlayer;
import com.example.livemictospeaker.player.LocalAudio;
import com.example.livemictospeaker.player.MediaPlayerUtils;
import com.example.livemictospeaker.player.adapter.AudioAdapter;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cancellable off-main MediaStore loading and URI-based, foreground-only preview. */
public final class LocalAudioPickerFragment extends Fragment {
    public static final Companion Companion = new Companion();
    public static final class Companion { public LocalAudioPickerFragment newInstance() { return new LocalAudioPickerFragment(); } }
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService loader;
    private CancellationSignal cancellation;
    private int generation;
    private AudioAdapter adapter;
    private PreviewPlayer preview;
    private LocalAudio selected;
    private RecyclerView list;
    private TextView empty, title, currentTime, totalTime;
    private ImageView play;
    private SeekBar seek;
    private SearchView search;
    private View controls;
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (preview == null || seek == null) return;
            if (!seek.isPressed()) seek.setProgress(preview.getPosition());
            currentTime.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer(preview.getPosition()));
            if (preview.wantsPlayback()) main.postDelayed(this, 250);
        }
    };
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.fragment_audio_list_new, container, false);
    }
    @Override public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        list = view.findViewById(R.id.list); empty = view.findViewById(R.id.no_audio);
        title = view.findViewById(R.id.title); currentTime = view.findViewById(R.id.current_time);
        totalTime = view.findViewById(R.id.total_time); play = view.findViewById(R.id.play_pause);
        seek = view.findViewById(R.id.player_seekbar); search = view.findViewById(R.id.search);
        controls = view.findViewById(R.id.mediaPlayerLayout);
        controls.setVisibility(View.GONE); seek.setEnabled(false); seek.setContentDescription("Preview position");
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        preview = new PreviewPlayer(requireContext(), new PreviewPlayer.Listener() {
            public void onChanged() { refresh(); }
            public void onCompleted() { refresh(); }
            public void onError() {
                if (isAdded()) Toast.makeText(requireContext(), "Unable to preview audio. Check file access or other audio apps.", Toast.LENGTH_LONG).show();
            }
        });
        play.setContentDescription("Play preview");
        play.setOnClickListener(v -> { if (preview.wantsPlayback()) preview.pause(); else preview.resume(); });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int position, boolean user) { }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { if (preview != null) preview.seekTo(bar.getProgress()); }
        });
        search.setQueryHint("Search audio"); search.setIconified(false); search.clearFocus();
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String query) { return false; }
            public boolean onQueryTextChange(String query) { if (adapter != null) adapter.getFilter().filter(query); return true; }
        });
        loadAudio();
    }
    private void loadAudio() {
        int ticket = ++generation;
        cancellation = new CancellationSignal();
        CancellationSignal signal = cancellation;
        Context app = requireContext().getApplicationContext();
        empty.setText("Loading audio…"); empty.setVisibility(View.VISIBLE); list.setVisibility(View.GONE);
        loader = Executors.newSingleThreadExecutor();
        loader.execute(() -> {
            ArrayList<LocalAudio> result = new ArrayList<>();
            String failure = null;
            String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_MODIFIED};
            try (Cursor cursor = app.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, MediaStore.Audio.Media.DATE_MODIFIED + " DESC", signal)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        signal.throwIfCanceled();
                        long id = cursor.getLong(0);
                        String name = cursor.getString(1);
                        if (name == null || name.isEmpty()) name = cursor.getString(2);
                        if (name == null || name.isEmpty()) name = "Audio";
                        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        result.add(new LocalAudio(id, name, cursor.getString(3), Math.max(0, cursor.getLong(4)),
                                uri.toString(), cursor.isNull(5) ? "0" : cursor.getString(5)));
                    }
                }
            } catch (android.os.OperationCanceledException ignored) { return; }
            catch (SecurityException error) { failure = "Allow audio access in app Settings to see your library."; }
            catch (RuntimeException error) { failure = "Could not load the audio library. Please reopen this screen."; }
            String message = failure;
            main.post(() -> {
                if (ticket != generation || getView() == null) return;
                adapter = new AudioAdapter(requireContext(), result, (audio, position, clicked) -> {
                    if (clicked.getId() == R.id.select_song) {
                        preview.pause();
                        startActivity(new Intent(requireContext(), MusicActivity.class)
                                .putExtra("SONG_URI", audio.getAudioUri()).putExtra("SONG_NAME", audio.getAudioTitle()));
                    } else choose(audio, position);
                    return null;
                });
                list.setAdapter(adapter);
                list.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
                empty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                empty.setText(message == null ? "No audio files found." : message);
                adapter.getFilter().filter(search.getQuery());
            });
        });
    }
    private void choose(LocalAudio audio, int position) {
        controls.setVisibility(View.VISIBLE);
        if (selected == audio && preview.hasTrack()) {
            if (preview.wantsPlayback()) preview.pause(); else preview.resume();
            return;
        }
        adapter.resetPreviouslyPlayedAudio(); selected = audio; audio.setHighlight(true); adapter.setCurrentPlayingPos(position);
        title.setText(audio.getAudioTitle()); preview.play(Uri.parse(audio.getAudioUri()));
    }
    private void refresh() {
        if (play == null || preview == null) return;
        boolean active = preview.wantsPlayback();
        if (selected != null) selected.setPlay(active);
        if (adapter != null) adapter.notifyDataSetChanged();
        play.setImageResource(active ? R.drawable.small_pause : R.drawable.play);
        play.setContentDescription(active ? "Pause preview" : "Play preview");
        int duration = preview.getDuration(); seek.setEnabled(duration > 0); seek.setMax(duration);
        totalTime.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer(duration));
        main.removeCallbacks(progress); progress.run();
    }
    @Override public void onStop() { if (preview != null) preview.pause(); main.removeCallbacks(progress); super.onStop(); }
    @Override public void onDestroyView() {
        generation++;
        if (cancellation != null) cancellation.cancel();
        if (loader != null) loader.shutdownNow();
        if (preview != null) preview.close(); preview = null;
        main.removeCallbacks(progress);
        if (list != null) list.setAdapter(null);
        adapter = null; selected = null; list = null; empty = null; title = null; currentTime = null;
        totalTime = null; play = null; seek = null; search = null; controls = null;
        super.onDestroyView();
    }
}
