package com.example.livemictospeaker.activity;

import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.example.livemictospeaker.player.SongListModel;
import com.example.livemictospeaker.player.adapter.SongListAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import demo.ads.GoogleAds;

public class MySavedHoldtoSpeakActivity extends AppCompatActivity {
    private MyPref myPref;
    private RecyclerView rvSongList;
    private SearchView search;
    private SongListAdapter songListAdapter;
    private List<SongListModel> songListModelList = new ArrayList<>();

    @Override 
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_record_audio_list_new);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));

        EUGeneralClass.BottomNavigationColor(this);
        this.myPref = new MyPref(this);

        findViewById(R.id.iv_back).setOnClickListener(view -> onBackPressed());

        this.rvSongList = findViewById(R.id.rvSongList);
        this.songListModelList = getMusicPlayer();
        this.songListAdapter = new SongListAdapter(this, this.songListModelList);
        this.rvSongList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        this.rvSongList.setItemAnimator(new DefaultItemAnimator());
        this.rvSongList.setNestedScrollingEnabled(false);
        this.rvSongList.setAdapter(this.songListAdapter);

        SearchView searchView = findViewById(R.id.search);
        this.search = searchView;
        if (searchView != null) {
            searchView.setActivated(false);
            searchView.setQueryHint("Type your filename here");
            searchView.onActionViewExpanded();
            searchView.setIconified(false);
            searchView.clearFocus();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override 
                public boolean onQueryTextSubmit(String str) {
                    return false;
                }

                @Override 
                public boolean onQueryTextChange(String str) {
                    filter(str);
                    return false;
                }
            });
        }
    }

    public static String convertMillieToHMmSs(long j) {
        long j2 = j / 1000;
        long j3 = j2 % 60;
        long j4 = (j2 / 60) % 60;
        long j5 = (j2 / 3600) % 24;
        if (j5 > 0) {
            return String.format("%02d:%02d:%02d", j5, j4, j3);
        }
        return String.format("%02d:%02d", j4, j3);
    }

    public List<SongListModel> getMusicPlayer() {
        List<SongListModel> arrayList = new ArrayList<>();
        List<File> searchDirs = new ArrayList<>();
        searchDirs.add(new File(MyPref.creatsDirsforholdspeak(this)));
        File legacyDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                getString(R.string.app_name) + "/HPRecording");
        if (legacyDir.exists() && legacyDir.isDirectory() && !legacyDir.equals(searchDirs.get(0))) {
            searchDirs.add(legacyDir);
        }

        for (File dir : searchDirs) {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".aac");
            });
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (File file : files) {
                    MediaMetadataRetriever retriever = null;
                    try {
                        retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(file.getAbsolutePath());
                        String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        long duration = durStr != null ? Long.parseLong(durStr) : 0;
                        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        if (title == null || title.isEmpty()) {
                            title = file.getName();
                        }
                        String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        if (artist == null) artist = "Unknown";
                        arrayList.add(new SongListModel(String.valueOf(file.hashCode()), artist, title, file.getAbsolutePath(), file.getName(), convertMillieToHMmSs(duration)));
                    } catch (Exception e) {
                        arrayList.add(new SongListModel(String.valueOf(file.hashCode()), "Unknown", file.getName(), file.getAbsolutePath(), file.getName(), "00:00"));
                    } finally {
                        if (retriever != null) {
                            try { retriever.release(); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
        return arrayList;
    }

    public void filter(String str) {
        ArrayList<SongListModel> arrayList = new ArrayList<>();
        for (SongListModel songListModel : this.songListModelList) {
            if (songListModel.getTitle().toLowerCase().contains(str.toLowerCase())
                    || songListModel.getDisplayName().toLowerCase().contains(str.toLowerCase())) {
                arrayList.add(songListModel);
            }
        }
        this.songListAdapter.filterList(arrayList);
    }

    @Override 
    protected void onResume() {
        super.onResume();
        this.songListModelList = getMusicPlayer();
        if (this.songListAdapter != null) {
            this.songListAdapter.filterList(new ArrayList<>(this.songListModelList));
        }
    }

    @Override 
    public void onBackPressed() {
        super.onBackPressed();
        AppConstants.overridePendingTransitionExit(this);
    }
}
