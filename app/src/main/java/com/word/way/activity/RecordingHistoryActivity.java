package com.word.way.activity;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.word.way.audio.RecordingChanges;
import com.word.way.audio.RecordingFiles;
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
import com.word.way.databinding.ActivityRecordAudioListNewBinding;
import com.word.way.util.SystemBars;
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
    private ActivityRecordAudioListNewBinding binding;
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
        binding = ActivityRecordAudioListNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().admobBanner(this, binding.nativeLay);
        binding.ivBack.setOnClickListener(v -> finish());
        ViewCompat.setAccessibilityHeading(binding.tvTitle, true);
        adapter = new SongListAdapter(this, new ArrayList<>(), this);
        binding.rvSongList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSongList.setAdapter(adapter);
        if (state != null) query = state.getString("historyQuery", "");
        search = binding.search;
        search.setQueryHint(getString(R.string.library_search_recordings));
        search.setIconified(false); search.setQuery(query, false); search.clearFocus();
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String value) { search.clearFocus(); return true; }
            public boolean onQueryTextChange(String value) { filter(value); return true; }
        });
        binding.libraryRetry.setOnClickListener(v -> reload());
        binding.libraryClear.setOnClickListener(v -> search.setQuery("", false));
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
    public static String formatDuration(long millis) {
        return com.word.way.util.TimeFormat.formatDuration(millis);
    }
    @Deprecated
    public static String convertMillieToHMmSs(long millis) {
        return formatDuration(millis);
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
            String name = file.getName(), artist = getString(R.string.library_unknown_artist), duration = formatDuration(0);
            try {
                metadata.setDataSource(file.getAbsolutePath());
                String title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (title != null && !title.isEmpty()) name = title;
                String author = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (author != null && !author.isEmpty()) artist = author;
                String value = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (value != null) duration = formatDuration(Long.parseLong(value));
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
        if (binding == null) return;
        LibraryState state = LibraryState.resolve(loading, failed, rows.size(), adapter.getItemCount());
        boolean content = state == LibraryState.CONTENT;
        binding.rvSongList.setVisibility(content ? View.VISIBLE : View.GONE);
        binding.libraryStatePanel.setVisibility(content ? View.GONE : View.VISIBLE);
        binding.libraryRetry.setVisibility(state == LibraryState.ERROR ? View.VISIBLE : View.GONE);
        binding.libraryClear.setVisibility(state == LibraryState.NO_MATCHES ? View.VISIBLE : View.GONE);
        binding.librarySummary.setVisibility(loading || failed ? View.GONE : View.VISIBLE);
        binding.librarySummary.setText(getResources().getQuantityString(R.plurals.library_count, adapter.getItemCount(), adapter.getItemCount()));
        int text = R.string.library_history_empty;
        if (state == LibraryState.LOADING) text = R.string.library_loading;
        else if (state == LibraryState.ERROR) text = R.string.library_history_error;
        else if (state == LibraryState.NO_MATCHES) text = R.string.library_no_matches;
        binding.noData.setText(text);
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
            File dir = recordingsDirectory();
            try {
                RecordingFiles.sweepEmptyPending(dir);
            } catch (RuntimeException ignored) { }
            // Recover recordings left as .pending by an interrupted save before listing history.
            try {
                if (RecordingRecovery.recover(dir, null, System.currentTimeMillis(),
                        RecordingHistoryActivity::isPlayable) > 0) RecordingChanges.notifySaved();
            } catch (RuntimeException ignored) { }

            int unrecoveredCount = 0;
            File unrecoveredSample = null;
            boolean canShareUnrecovered = false;
            if (dir != null && dir.isDirectory()) {
                File[] unrecoveredFiles = dir.listFiles((d, name) -> name.endsWith(RecordingRecovery.UNRECOVERED_SUFFIX));
                if (unrecoveredFiles != null) {
                    unrecoveredCount = unrecoveredFiles.length;
                    for (File f : unrecoveredFiles) {
                        if (f.isFile() && f.length() > 0) {
                            unrecoveredSample = f;
                            break;
                        }
                    }
                }
                if (unrecoveredSample != null) {
                    try {
                        FileProvider.getUriForFile(RecordingHistoryActivity.this, getPackageName() + ".provider", unrecoveredSample);
                        canShareUnrecovered = true;
                    } catch (IllegalArgumentException | SecurityException notCovered) {
                        canShareUnrecovered = false;
                    }
                }
            }
            final int unrecoveredTotal = unrecoveredCount;
            final boolean shareEnabled = canShareUnrecovered;
            final File unrecoveredShareFile = unrecoveredSample;

            List<SongListModel> found;
            boolean failure;
            try { found = getMusicPlayer(); failure = false; }
            catch (RuntimeException error) { found = new ArrayList<>(); failure = true; }
            final List<SongListModel> result = found;
            final boolean unavailable = failure;
            runOnUiThread(() -> {
                if (!isDestroyed() && resumed && ticket == generation) {
                    rows = result; loading = false; failed = unavailable; filter(query);
                    renderUnrecoveredBanner(unrecoveredTotal, shareEnabled, unrecoveredShareFile);
                }
            });
        });
    }

    private void renderUnrecoveredBanner(int count, boolean shareAvailable, File shareFile) {
        if (binding == null) return;
        if (count <= 0) {
            binding.unrecoveredBanner.setVisibility(View.GONE);
            return;
        }
        binding.unrecoveredBanner.setVisibility(View.VISIBLE);
        binding.unrecoveredTitle.setText(getResources().getQuantityString(R.plurals.unrecovered_notice, count, count));
        if (shareAvailable && shareFile != null) {
            binding.unrecoveredShare.setVisibility(View.VISIBLE);
            binding.unrecoveredShare.setOnClickListener(v -> shareUnrecovered(shareFile));
        } else {
            binding.unrecoveredShare.setVisibility(View.GONE);
        }
        binding.unrecoveredDelete.setOnClickListener(v -> confirmDeleteUnrecovered());
    }

    private void confirmDeleteUnrecovered() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unrecovered_delete_dialog_title)
                .setMessage(R.string.unrecovered_delete_dialog_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unrecovered_delete_dialog_confirm, (dialog, which) -> deleteUnrecoveredFiles())
                .show();
    }

    private void deleteUnrecoveredFiles() {
        loader.execute(() -> {
            File dir = recordingsDirectory();
            if (dir != null && dir.isDirectory()) {
                File[] unrecovered = dir.listFiles((d, name) -> name.endsWith(RecordingRecovery.UNRECOVERED_SUFFIX));
                if (unrecovered != null) {
                    for (File f : unrecovered) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        } catch (SecurityException ignored) { }
                    }
                }
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                reload();
            });
        });
    }

    private void shareUnrecovered(File file) {
        if (file == null || !file.isFile()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(getContentResolver(), getString(R.string.unrecovered_share_title), uri));
            startActivity(Intent.createChooser(send, getString(R.string.unrecovered_share_title)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.library_share_unavailable, Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException | SecurityException error) {
            Toast.makeText(this, R.string.library_share_restricted, Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onDestroy() {
        generation++; if (pending != null) pending.cancel(true); loader.shutdownNow(); super.onDestroy();
    }
}
