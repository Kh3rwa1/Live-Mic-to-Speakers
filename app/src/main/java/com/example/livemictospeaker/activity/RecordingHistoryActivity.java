package com.example.livemictospeaker.activity;

import android.media.MediaMetadataRetriever;
import com.example.livemictospeaker.audio.RecordingChanges;
import android.os.Bundle;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.player.AudioSearch;
import com.example.livemictospeaker.player.SongListModel;
import com.example.livemictospeaker.player.adapter.SongListAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import demo.ads.GoogleAds;

/** Shared history loader: no directory walks or metadata extraction on the UI thread. */
public abstract class RecordingHistoryActivity extends AppCompatActivity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Future<?> pending;
    private int generation;
    private SongListAdapter adapter;
    private List<SongListModel> rows = new ArrayList<>();
    private String query = "";
    private boolean resumed;
    protected abstract File recordingsDirectory();
    protected abstract String legacyFolder();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_record_audio_list_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        findViewById(R.id.iv_back).setContentDescription("Back");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        RecyclerView list = findViewById(R.id.rvSongList);
        adapter = new SongListAdapter(this, new ArrayList<>());
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        SearchView search = findViewById(R.id.search);
        if (search != null) {
            search.setQueryHint("Search recordings"); search.setIconified(false); search.clearFocus();
            search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                public boolean onQueryTextSubmit(String value) { return false; }
                public boolean onQueryTextChange(String value) { filter(value); return true; }
            });
        }
        RecordingChanges.revisions().observe(this, revision -> { if (resumed) reload(); });
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
        for (File directory : Arrays.asList(current, legacy)) {
            if (Thread.currentThread().isInterrupted()) return found;
            File[] children;
            try {
                children = directory.listFiles((parent, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac");
                });
            } catch (SecurityException ignored) { continue; }
            if (children != null) for (File file : children) if (file.isFile() && !files.contains(file)) files.add(file);
        }
        files.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (File file : files) {
            if (Thread.currentThread().isInterrupted()) break;
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            String name = file.getName(), artist = "Unknown", duration = "00:00";
            try {
                metadata.setDataSource(file.getAbsolutePath());
                String title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (title != null && !title.isEmpty()) name = title;
                String author = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (author != null) artist = author;
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
        if (adapter != null) adapter.filterList(filtered);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; reload(); }
    @Override protected void onPause() {
        resumed = false;
        generation++;
        if (pending != null) pending.cancel(true);
        super.onPause();
    }
    private void reload() {
        int ticket = ++generation;
        if (pending != null) pending.cancel(true);
        pending = loader.submit(() -> {
            List<SongListModel> found = getMusicPlayer();
            runOnUiThread(() -> { if (!isDestroyed() && ticket == generation) { rows = found; filter(query); } });
        });
    }
    @Override protected void onDestroy() { generation++; if (pending != null) pending.cancel(true); loader.shutdownNow(); super.onDestroy(); }
}
