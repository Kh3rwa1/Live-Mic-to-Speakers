package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.example.livemictospeaker.audio.RecordingSession;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import demo.ads.GoogleAds;

public class RecordAudioActivity extends AppCompatActivity {
    private final RecordingSession recording = new RecordingSession();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView toggle, play;
    private TextView timer, label;
    private MediaPlayer preview;
    private File lastSaved;
    private long startedAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!recording.isRecording()) return;
            long seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000;
            timer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
            handler.postDelayed(this, 250);
        }
    };
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> message(granted
                    ? "Permission granted. Tap Start to record."
                    : "Allow microphone access in Settings to record."));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_record_audio_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        toggle = findViewById(R.id.iv_start_stop_new);
        play = findViewById(R.id.iv_play);
        timer = findViewById(R.id.tv_timer);
        label = findViewById(R.id.tv_start_stop_new);
        findViewById(R.id.iv_back).setContentDescription("Back");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.iv_history).setContentDescription("Saved recordings");
        findViewById(R.id.iv_history).setOnClickListener(v -> startActivity(new Intent(this, MySavedAnnounceActivity.class)));
        if (state != null && state.getString("lastSaved") != null) lastSaved = new File(state.getString("lastSaved"));
        toggle.setOnClickListener(v -> {
            if (recording.isRecording()) { stopRecording(true); return; }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.RECORD_AUDIO); return;
            }
            stopPreview();
            try {
                recording.start(this, new File(MyPref.creatsDirsforApp(this)));
                startedAt = SystemClock.elapsedRealtime();
                handler.post(tick);
            } catch (IOException error) { message(error.getMessage()); }
            updateControls();
        });
        play.setOnClickListener(v -> { if (preview != null) stopPreview(); else playRecording(); });
        updateControls();
        stopPreview();
    }
    private void stopRecording(boolean notify) {
        handler.removeCallbacks(tick);
        if (!recording.isRecording()) return;
        try {
            File saved = recording.stop(true);
            if (saved != null) { lastSaved = saved; if (notify) message("Recording saved"); }
            else if (notify) message("Recording too short; incomplete file removed.");
        } catch (IOException error) { message(error.getMessage()); }
        updateControls();
    }
    private void updateControls() {
        boolean active = recording.isRecording();
        toggle.setImageResource(active ? R.drawable.click_time_start : R.drawable.time_start);
        toggle.setContentDescription(active ? "Stop recording" : "Start recording");
        label.setText(active ? "Stop" : "Start");
        play.setEnabled(!active);
    }
    private void playRecording() {
        if (recording.isRecording()) return;
        if (lastSaved == null || !lastSaved.isFile()) { message("No saved recording. Record first."); return; }
        MediaPlayer player = new MediaPlayer();
        preview = player;
        try {
            player.setDataSource(lastSaved.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                if (preview == mp) { mp.start(); play.setImageResource(R.drawable.pause); play.setContentDescription("Stop playback"); }
            });
            player.setOnCompletionListener(mp -> stopPreview());
            player.setOnErrorListener((mp, what, extra) -> { stopPreview(); message("Could not play recording"); return true; });
            player.prepareAsync();
        } catch (IOException | RuntimeException error) { stopPreview(); message("Could not open recording"); }
    }
    private void stopPreview() {
        if (preview != null) { preview.release(); preview = null; }
        if (play != null) { play.setImageResource(R.drawable.play); play.setContentDescription("Play last recording"); }
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (lastSaved != null) state.putString("lastSaved", lastSaved.getAbsolutePath());
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() { stopRecording(false); stopPreview(); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacks(tick); recording.close(); stopPreview(); super.onDestroy(); }
}
