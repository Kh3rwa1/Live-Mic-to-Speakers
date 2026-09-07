package com.example.livemictospeaker.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Service.MediaPlaybackService;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.example.livemictospeaker.player.SongListModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import demo.ads.GoogleAds;

public class MusicActivity extends AppCompatActivity {
    private ImageView albumArt;
    private TextView artistTextView;
    private ImageView buttonNext;
    private ImageView buttonPlayPause;
    private ImageView buttonPre;
    private TextView durationTextView;
    private SharedPreferences.Editor editor;
    private int elapsedTime = 0;
    private AppCompatSeekBar elapsedTimeSeekBar;
    private TextView elapsedTimeTextView;
    private RelativeLayout fab;
    private int index = 0;
    private MyPref myPref;
    private BroadcastReceiver receiverCompleted;
    private BroadcastReceiver receiverElapsedTime;
    private SharedPreferences settingPreferences;
    private List<SongListModel> songListModelList = new ArrayList<>();
    private String songname;
    private TextView titleTextView;
    private Uri pendingTrackUri = null;
    private Uri currentTrack = null;
    private MediaPlaybackService playbackService = null;
    private boolean isServiceBound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            playbackService = ((MediaPlaybackService.IDBinder) binder).getService();
            isServiceBound = true;
            if (pendingTrackUri != null && playbackService != null) {
                currentTrack = pendingTrackUri;
                playbackService.init(pendingTrackUri);
                initInfos(pendingTrackUri);
                pendingTrackUri = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playbackService = null;
            isServiceBound = false;
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_music_new);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));

        EUGeneralClass.BottomNavigationColor(this);
        this.myPref = new MyPref(this);

        findViewById(R.id.iv_back).setOnClickListener(view -> onBackPressed());

        this.songname = getIntent().getStringExtra("SONG_NAME");
        String songUriStr = getIntent().getStringExtra("SONG_URI");
        this.index = getIntent().getIntExtra("SONG_INDEX", 0);

        this.receiverElapsedTime = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                elapsedTime = intent.getIntExtra(MediaPlaybackService.MPS_MESSAGE, 0);
                updateElapsedTime(elapsedTime);
            }
        };

        this.receiverCompleted = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                clearInfos();
            }
        };

        this.settingPreferences = getApplicationContext().getSharedPreferences("playIndex", Context.MODE_PRIVATE);
        this.editor = settingPreferences.edit();
        this.songListModelList = getMusicPlayer(songUriStr);

        this.buttonPlayPause = findViewById(R.id.imageButtonPlayPause);
        this.buttonNext = findViewById(R.id.imageButtonNext);
        this.buttonPre = findViewById(R.id.imageButtonPre);
        this.albumArt = findViewById(R.id.albumArt);
        this.titleTextView = findViewById(R.id.textViewTitle);
        this.titleTextView.setText(this.songname != null ? this.songname : "Audio Track");
        this.artistTextView = findViewById(R.id.textViewArtist);
        this.elapsedTimeTextView = findViewById(R.id.textViewElapsedTime);
        this.durationTextView = findViewById(R.id.textViewDuration);
        this.elapsedTimeSeekBar = findViewById(R.id.seekBar);

        this.buttonPlayPause.setOnClickListener(view -> {
            MediaPlaybackService service = playbackService;
            if (service == null) return;
            if (service.isPlaying()) {
                buttonPlayPause.setImageResource(R.drawable.play);
                service.pause();
            } else {
                buttonPlayPause.setImageResource(R.drawable.pause);
                service.play();
            }
        });
        this.buttonPlayPause.setEnabled(true);

        this.buttonNext.setOnClickListener(view -> {
            if (songListModelList != null && index < songListModelList.size() - 1) {
                index++;
                playTrackAtIndex(index);
            } else {
                Toast.makeText(getApplicationContext(), "No more songs", Toast.LENGTH_SHORT).show();
            }
        });

        this.buttonPre.setOnClickListener(view -> {
            if (index > 0 && songListModelList != null) {
                index--;
                playTrackAtIndex(index);
            }
        });

        this.elapsedTimeSeekBar.setEnabled(false);
        this.elapsedTimeSeekBar.setProgress(0);
        this.elapsedTimeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean z) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MediaPlaybackService service = playbackService;
                if (service != null) {
                    service.seekTo(seekBar.getProgress());
                }
            }
        });

        this.fab = findViewById(R.id.fab);
        if (this.fab != null) {
            this.fab.setOnClickListener(view -> finish());
        }

        if (songUriStr != null) {
            Uri fromFile = Uri.fromFile(new File(songUriStr));
            MediaPlaybackService service = playbackService;
            if (service != null) {
                currentTrack = fromFile;
                service.init(fromFile);
                initInfos(fromFile);
            } else {
                pendingTrackUri = fromFile;
            }
            saveLastIndexPlay(this.index);
        }
    }

    private void playTrackAtIndex(int idx) {
        if (songListModelList == null || idx < 0 || idx >= songListModelList.size()) return;
        SongListModel model = songListModelList.get(idx);
        this.songname = model.getDisplayName();
        Uri fromFile = Uri.fromFile(new File(model.getData()));
        currentTrack = fromFile;
        MediaPlaybackService service = playbackService;
        if (service != null) {
            service.init(fromFile);
        }
        initInfos(fromFile);
        saveLastIndexPlay(idx);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (playbackService == null && !isServiceBound) {
            try {
                Intent serviceIntent = new Intent(getApplicationContext(), MediaPlaybackService.class);
                bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(this.receiverElapsedTime, new IntentFilter(MediaPlaybackService.MPS_RESULT));
        LocalBroadcastManager.getInstance(this).registerReceiver(this.receiverCompleted, new IntentFilter(MediaPlaybackService.MPS_COMPLETED));
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.receiverElapsedTime);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.receiverCompleted);
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isServiceBound) {
            try {
                unbindService(serviceConn);
            } catch (Exception ignored) {}
            isServiceBound = false;
            playbackService = null;
        }
    }

    private String secondsToString(int i) {
        int i2 = i / 1000;
        return String.format("%2d:%02d", i2 / 60, i2 % 60);
    }

    public void initInfos(Uri uri) {
        if (uri == null) return;
        MediaMetadataRetriever mediaMetadataRetriever = null;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(this, uri);
            String durStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            int parseInt = durStr != null ? Integer.parseInt(durStr) : 0;
            this.titleTextView.setText(this.songname != null ? this.songname : "Audio Track");
            String artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            this.artistTextView.setText(artist != null ? artist : "Unknown Artist");
            this.durationTextView.setText(secondsToString(parseInt));
            this.elapsedTimeSeekBar.setMax(parseInt);
            this.elapsedTimeSeekBar.setEnabled(true);
            this.albumArt.setImageResource(R.drawable.music);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mediaMetadataRetriever != null) {
                try {
                    mediaMetadataRetriever.release();
                } catch (Exception ignored) {}
            }
        }
    }

    public void updateElapsedTime(int i) {
        this.elapsedTimeSeekBar.setProgress(i);
        this.elapsedTimeTextView.setText(secondsToString(i));
        MediaPlaybackService service = playbackService;
        if (service != null && service.isPlaying()) {
            this.buttonPlayPause.setEnabled(true);
            this.buttonPlayPause.setImageResource(R.drawable.pause);
        }
    }

    public void clearInfos() {
        this.durationTextView.setText("");
        this.elapsedTimeTextView.setText("");
        this.titleTextView.setText("-");
        this.artistTextView.setText("-");
        this.elapsedTime = 0;
        this.elapsedTimeSeekBar.setEnabled(false);
        this.elapsedTimeSeekBar.setProgress(0);
        this.albumArt.setImageResource(R.drawable.music);
        this.buttonPlayPause.setEnabled(false);
        this.buttonPlayPause.setImageResource(R.drawable.play);
    }

    public List<SongListModel> getMusicPlayer(String songUriStr) {
        List<SongListModel> arrayList = new ArrayList<>();
        File parentDir = null;
        if (songUriStr != null) {
            File f = new File(songUriStr);
            if (f.exists() && f.getParentFile() != null) {
                parentDir = f.getParentFile();
            }
        }
        if (parentDir == null || !parentDir.exists()) {
            parentDir = new File(MyPref.creatsDirsforApp(this));
        }

        File[] files = parentDir.listFiles((dir, name) -> {
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
                    arrayList.add(new SongListModel(String.valueOf(file.hashCode()), artist, title, file.getAbsolutePath(), file.getName(), timeConversion(duration)));
                } catch (Exception e) {
                    arrayList.add(new SongListModel(String.valueOf(file.hashCode()), "Unknown", file.getName(), file.getAbsolutePath(), file.getName(), "00:00"));
                } finally {
                    if (retriever != null) {
                        try { retriever.release(); } catch (Exception ignored) {}
                    }
                }
            }
        }
        return arrayList;
    }

    public String timeConversion(long j) {
        int i = (int) j;
        int i2 = i / 3600000;
        int i3 = (i / 60000) % 60000;
        int i4 = (i % 60000) / 1000;
        if (i2 > 0) {
            return String.format("%02d:%02d:%02d", i2, i3, i4);
        }
        return String.format("%02d:%02d", i3, i4);
    }

    public void saveLastIndexPlay(int i) {
        this.editor.putInt("lastIndex", i);
        this.editor.apply();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        AppConstants.overridePendingTransitionExit(this);
    }
}
