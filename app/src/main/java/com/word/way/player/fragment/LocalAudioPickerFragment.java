package com.word.way.player.fragment;

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
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.word.way.R;
import com.word.way.Utils.ToolUi;
import com.word.way.activity.MusicActivity;
import com.word.way.audio.PreviewPlayer;
import com.word.way.player.LibraryState;
import com.word.way.player.LocalAudio;
import com.word.way.player.MediaPlayerUtils;
import com.word.way.player.adapter.AudioAdapter;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cancellable MediaStore loading, visible search/recovery, and foreground-only preview. */
public final class LocalAudioPickerFragment extends Fragment {
    public static final Companion Companion = new Companion();
    public static final class Companion { public LocalAudioPickerFragment newInstance() { return new LocalAudioPickerFragment(); } }
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService loader;
    private CancellationSignal cancellation;
    private int generation, totalCount, failureMessage;
    private boolean loading;
    private String query = "";
    private AudioAdapter adapter;
    private PreviewPlayer preview;
    private LocalAudio selected;
    private RecyclerView list;
    private TextView empty, title, currentTime, totalTime, summary, previewFeedback;
    private ImageView play;
    private SeekBar seek;
    private SearchView search;
    private View controls, statePanel, retry, clear, permissions;
    private final RecyclerView.AdapterDataObserver changes = new RecyclerView.AdapterDataObserver() {
        @Override public void onChanged() { renderListState(); }
    };
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
        if (state != null) query = state.getString("libraryQuery", "");
        list = view.findViewById(R.id.list); empty = view.findViewById(R.id.no_audio);
        title = view.findViewById(R.id.title); currentTime = view.findViewById(R.id.current_time);
        totalTime = view.findViewById(R.id.total_time); play = view.findViewById(R.id.play_pause);
        seek = view.findViewById(R.id.player_seekbar); search = view.findViewById(R.id.search);
        summary = view.findViewById(R.id.library_summary); previewFeedback = view.findViewById(R.id.library_preview_feedback);
        controls = view.findViewById(R.id.mediaPlayerLayout); statePanel = view.findViewById(R.id.library_state_panel);
        retry = view.findViewById(R.id.library_retry); clear = view.findViewById(R.id.library_clear);
        permissions = view.findViewById(R.id.library_permissions);
        controls.setVisibility(View.GONE); seek.setEnabled(false);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        loader = Executors.newSingleThreadExecutor();
        preview = new PreviewPlayer(requireContext(), new PreviewPlayer.Listener() {
            public void onChanged() { refresh(); }
            public void onCompleted() { refresh(); }
            public void onError() {
                if (previewFeedback != null) previewFeedback.setText(R.string.library_preview_error);
            }
        });
        play.setOnClickListener(v -> { if (preview.wantsPlayback()) preview.pause(); else preview.resume(); });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int position, boolean user) { }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { if (preview != null) preview.seekTo(bar.getProgress()); }
        });
        search.setQueryHint(getString(R.string.library_search_audio));
        search.setIconified(false); search.setQuery(query, false); search.clearFocus();
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String value) { search.clearFocus(); return true; }
            public boolean onQueryTextChange(String value) {
                query = value == null ? "" : value;
                if (adapter != null) adapter.getFilter().filter(query);
                return true;
            }
        });
        retry.setOnClickListener(v -> loadAudio());
        clear.setOnClickListener(v -> search.setQuery("", false));
        permissions.setOnClickListener(v -> ToolUi.permissionDenied(requireActivity()));
        loadAudio();
    }
    private void loadAudio() {
        if (getView() == null || loader == null || loader.isShutdown()) return;
        int ticket = ++generation;
        if (cancellation != null) cancellation.cancel();
        cancellation = new CancellationSignal();
        CancellationSignal signal = cancellation;
        Context app = requireContext().getApplicationContext();
        if (preview != null) preview.stop();
        selected = null; controls.setVisibility(View.GONE);
        if (adapter != null) adapter.unregisterAdapterDataObserver(changes);
        adapter = null; list.setAdapter(null);
        loading = true; failureMessage = 0; totalCount = 0; renderListState();
        loader.execute(() -> {
            ArrayList<LocalAudio> result = new ArrayList<>();
            int failure = 0;
            String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_MODIFIED};
            try (Cursor cursor = app.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, MediaStore.Audio.Media.DATE_MODIFIED + " DESC", signal)) {
                if (cursor == null) throw new IllegalStateException("Media provider returned no cursor");
                while (cursor.moveToNext()) {
                    signal.throwIfCanceled();
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    if (name == null || name.isEmpty()) name = cursor.getString(2);
                    if (name == null || name.isEmpty()) name = app.getString(R.string.library_untitled);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    result.add(new LocalAudio(id, name, cursor.getString(3), Math.max(0, cursor.getLong(4)),
                            uri.toString(), cursor.isNull(5) ? "0" : cursor.getString(5)));
                }
            } catch (android.os.OperationCanceledException ignored) { return; }
            catch (SecurityException error) { failure = R.string.library_permission_error; }
            catch (RuntimeException error) { failure = R.string.library_load_error; }
            final int message = failure;
            main.post(() -> {
                if (ticket != generation || getView() == null) return;
                // Never present a partial query result as a successful library load.
                if (message != 0) result.clear();
                loading = false; failureMessage = message; totalCount = result.size();
                adapter = new AudioAdapter(requireContext(), result, (audio, position, clicked) -> {
                    if (clicked.getId() == R.id.select_song) {
                        preview.pause();
                        startActivity(new Intent(requireContext(), MusicActivity.class)
                                .putExtra("SONG_URI", audio.getAudioUri()).putExtra("SONG_NAME", audio.getAudioTitle()));
                    } else choose(audio, position);
                });
                adapter.registerAdapterDataObserver(changes);
                list.setAdapter(adapter);
                renderListState();
                adapter.getFilter().filter(query);
            });
        });
    }
    private void renderListState() {
        if (empty == null) return;
        int visibleCount = adapter == null ? 0 : adapter.getItemCount();
        LibraryState state = LibraryState.resolve(loading, failureMessage != 0, totalCount, visibleCount);
        boolean content = state == LibraryState.CONTENT;
        list.setVisibility(content ? View.VISIBLE : View.GONE);
        statePanel.setVisibility(content ? View.GONE : View.VISIBLE);
        retry.setVisibility(state == LibraryState.ERROR || state == LibraryState.EMPTY ? View.VISIBLE : View.GONE);
        clear.setVisibility(state == LibraryState.NO_MATCHES ? View.VISIBLE : View.GONE);
        permissions.setVisibility(state == LibraryState.ERROR && failureMessage == R.string.library_permission_error ? View.VISIBLE : View.GONE);
        summary.setVisibility(loading || failureMessage != 0 ? View.GONE : View.VISIBLE);
        summary.setText(getResources().getQuantityString(R.plurals.library_count, visibleCount, visibleCount));
        int text = R.string.library_empty;
        if (state == LibraryState.LOADING) text = R.string.library_loading;
        else if (state == LibraryState.ERROR) text = failureMessage;
        else if (state == LibraryState.NO_MATCHES) text = R.string.library_no_matches;
        empty.setText(text);
    }
    private void choose(LocalAudio audio, int position) {
        controls.setVisibility(View.VISIBLE);
        previewFeedback.setText(R.string.library_preview_help);
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
        play.setImageResource(active ? R.drawable.tool_pause : R.drawable.tool_play);
        play.setContentDescription(getString(active ? R.string.library_pause_preview : R.string.library_play_preview));
        ToolUi.enabled(play, preview.hasTrack());
        int duration = preview.getDuration(); seek.setEnabled(duration > 0); seek.setMax(duration);
        totalTime.setText(MediaPlayerUtils.INSTANCE.milliSecondsToTimer(duration));
        main.removeCallbacks(progress); progress.run();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putString("libraryQuery", query); super.onSaveInstanceState(state);
    }
    @Override public void onResume() {
        super.onResume();
        if (failureMessage == R.string.library_permission_error) loadAudio();
    }
    @Override public void onStop() { if (preview != null) preview.pause(); main.removeCallbacks(progress); super.onStop(); }
    @Override public void onDestroyView() {
        generation++;
        if (cancellation != null) cancellation.cancel();
        if (loader != null) loader.shutdownNow(); loader = null;
        if (adapter != null) adapter.unregisterAdapterDataObserver(changes);
        if (preview != null) preview.close(); preview = null;
        main.removeCallbacks(progress);
        if (list != null) list.setAdapter(null);
        adapter = null; selected = null; list = null; empty = null; title = null; currentTime = null;
        totalTime = null; play = null; seek = null; search = null; controls = null;
        summary = null; previewFeedback = null; statePanel = null; retry = null; clear = null; permissions = null;
        super.onDestroyView();
    }
}
