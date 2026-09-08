package com.word.way.activity;

import android.media.MediaMetadataRetriever;
import com.word.way.audio.RecordingChanges;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
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
public abstract class RecordingHistoryActivity extends AppCompatActivity {
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
        adapter = new SongListAdapter(this, new ArrayList<>());
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
