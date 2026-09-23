package com.word.way.activity;

import android.media.MediaMetadataRetriever;
import com.word.way.audio.RecordingChanges;
import com.word.way.audio.RecordingNames;
import com.word.way.audio.RecordingRecovery;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.player.AudioSearch;
import com.word.way.player.LibraryState;
import com.word.way.player.SongListModel;
import com.word.way.player.adapter.SongListAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import demo.ads.GoogleAds;

/** Shared, cancellable history loading with explicit loading/empty/search/error states. */
public abstract class RecordingHistoryActivity extends AppCompatActivity implements SongListAdapter.Actions {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Future<?> pending;
    private int generation;
    private SongListAdapter adapter;
    private List<SongListModel> rows = new ArrayList<>();
    private String query = "";
    private boolean resumed, loading = true, failed;
    private SearchView search;
    protected abstract File recordingsDirectory();
    protected abstract String legacyFolder();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_record_audio_list_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tv_tittle), true);
        RecyclerView list = findViewById(R.id.rvSongList);
        adapter = new SongListAdapter(this, new ArrayList<>(), this);
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        if (state != null) query = state.getString("historyQuery", "");
        search = findViewById(R.id.search);
        search.setQueryHint(getString(R.string.library_search_recordings));
        search.setIconified(false); search.setQuery(query, false); search.clearFocus();
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String value) { search.clearFocus(); return true; }
            public boolean onQueryTextChange(String value) { filter(value); return true; }
        });
        findViewById(R.id.library_retry).setOnClickListener(v -> reload());
        findViewById(R.id.library_clear).setOnClickListener(v -> search.setQuery("", false));
        RecordingChanges.revisions().observe(this, revision -> { if (resumed) reload(); });
        renderState();
    }
    /** True when the platform can read the file as audio; used to validate recovered recordings. */
    private static boolean isPlayable(File file) {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(file.getAbsolutePath());
            return metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) != null;
        } catch (RuntimeException error) {
            return false;
        } finally {
            try { metadata.release(); } catch (Exception ignored) { }
        }
    }
    @Override public void onRename(SongListModel audio) {
        if (audio == null) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(audio.getDisplayName());
        input.setHint(R.string.library_rename_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.library_rename_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.library_rename_confirm,
                        (dialog, which) -> performRename(audio, input.getText().toString()))
                .show();
    }
    @Override public void onDelete(SongListModel audio) {
        if (audio == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.library_delete_title)
                .setMessage(getString(R.string.library_delete_message, audio.getDisplayName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.library_delete_confirm, (dialog, which) -> performDelete(audio))
                .show();
    }
    private void performRename(SongListModel audio, String requested) {
        String name = requested == null ? "" : requested.trim();
        if (name.isEmpty()) { Toast.makeText(this, R.string.library_rename_empty, Toast.LENGTH_SHORT).show(); return; }
        File source = new File(audio.getData());
        loader.execute(() -> {
            boolean success = false;
            try {
                File target = RecordingNames.renamedTarget(source, name);
                success = target != null && source.renameTo(target);
            } catch (RuntimeException ignored) { }
            final boolean renamed = success;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (renamed) { RecordingChanges.notifySaved(); reload(); }
                else Toast.makeText(this, R.string.library_rename_failed, Toast.LENGTH_LONG).show();
            });
        });
    }
    private void performDelete(SongListModel audio) {
        File target = new File(audio.getData());
        loader.execute(() -> {
            boolean success = false;
            try { success = target.isFile() && target.delete(); }
            catch (RuntimeException ignored) { }
            final boolean deleted = success;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (deleted) { RecordingChanges.notifySaved(); reload(); }
                else Toast.makeText(this, R.string.library_delete_failed, Toast.LENGTH_LONG).show();
            });
        });
    }
    public static String convertMillieToHMmSs(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return seconds >= 3600 ? String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
                : String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
    public List<SongListModel> getMusicPlayer() {
        List<SongListModel> found = new ArrayList<>();
        File current = recordingsDirectory();
        File legacy = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), getString(R.string.app_name) + "/" + legacyFolder());
        List<File> files = new ArrayList<>();
        Set<File> seen = new HashSet<>();
        for (File directory : Arrays.asList(current, legacy)) {
            if (Thread.currentThread().isInterrupted()) return found;
            File[] children;
            try {
                children = directory.listFiles((parent, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac");
                });
                if (directory.equals(current) && children == null && directory.isDirectory())
                    throw new IllegalStateException("Recordings directory could not be read");
            } catch (SecurityException error) {
                if (directory.equals(current)) throw error;
                continue; // Legacy public folders are optional, never a reason to broaden access.
            }
            if (children != null) for (File file : children) if (file.isFile() && seen.add(file)) files.add(file);
        }
        files.sort(Comparator.comparingLong(File::lastModified).reversed()
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            if (Thread.currentThread().isInterrupted()) break;
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            String name = file.getName(), artist = getString(R.string.library_unknown_artist), duration = convertMillieToHMmSs(0);
            try {
                metadata.setDataSource(file.getAbsolutePath());
                String title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (title != null && !title.isEmpty()) name = title;
                String author = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (author != null && !author.isEmpty()) artist = author;
                String value = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (value != null) duration = convertMillieToHMmSs(Long.parseLong(value));
            } catch (RuntimeException ignored) { }
            finally { try { metadata.release(); } catch (Exception ignored) { } }
            found.add(new SongListModel(String.valueOf(file.hashCode()), artist, name, file.getAbsolutePath(), file.getName(), duration));
        }
        return found;
    }
    public void filter(String value) {
        query = value == null ? "" : value;
        ArrayList<SongListModel> filtered = new ArrayList<>();
        for (SongListModel row : rows) if (AudioSearch.matches(row.getTitle(), query) || AudioSearch.matches(row.getDisplayName(), query)) filtered.add(row);
        if (adapter != null) { adapter.filterList(filtered); renderState(); }
    }
    private void renderState() {
        LibraryState state = LibraryState.resolve(loading, failed, rows.size(), adapter.getItemCount());
        boolean content = state == LibraryState.CONTENT;
        findViewById(R.id.rvSongList).setVisibility(content ? View.VISIBLE : View.GONE);
        findViewById(R.id.library_state_panel).setVisibility(content ? View.GONE : View.VISIBLE);
        findViewById(R.id.library_retry).setVisibility(state == LibraryState.ERROR ? View.VISIBLE : View.GONE);
        findViewById(R.id.library_clear).setVisibility(state == LibraryState.NO_MATCHES ? View.VISIBLE : View.GONE);
        TextView summary = findViewById(R.id.library_summary);
        summary.setVisibility(loading || failed ? View.GONE : View.VISIBLE);
        summary.setText(getResources().getQuantityString(R.plurals.library_count, adapter.getItemCount(), adapter.getItemCount()));
        int text = R.string.library_history_empty;
        if (state == LibraryState.LOADING) text = R.string.library_loading;
        else if (state == LibraryState.ERROR) text = R.string.library_history_error;
        else if (state == LibraryState.NO_MATCHES) text = R.string.library_no_matches;
        ((TextView) findViewById(R.id.noData)).setText(text);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("historyQuery", query); super.onSaveInstanceState(state);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; reload(); }
    // Consent stays on home screens; history banners still render once consent allows ads.
    @Override protected void onPause() {
        resumed = false; generation++;
        if (pending != null) pending.cancel(true);
        super.onPause();
    }
    private void reload() {
        if (!resumed || isDestroyed()) return;
        int ticket = ++generation;
        if (pending != null) pending.cancel(true);
        loading = true; failed = false; renderState();
        pending = loader.submit(() -> {
            // Recover recordings left as .pending by an interrupted save before listing history.
            try {
                if (RecordingRecovery.recover(recordingsDirectory(), null, System.currentTimeMillis(),
                        RecordingHistoryActivity::isPlayable) > 0) RecordingChanges.notifySaved();
            } catch (RuntimeException ignored) { }
            List<SongListModel> found;
            boolean failure;
            try { found = getMusicPlayer(); failure = false; }
            catch (RuntimeException error) { found = new ArrayList<>(); failure = true; }
            final List<SongListModel> result = found;
            final boolean unavailable = failure;
            runOnUiThread(() -> {
                if (!isDestroyed() && resumed && ticket == generation) {
                    rows = result; loading = false; failed = unavailable; filter(query);
                }
            });
        });
    }
    @Override protected void onDestroy() {
        generation++; if (pending != null) pending.cancel(true); loader.shutdownNow(); super.onDestroy();
    }
}
