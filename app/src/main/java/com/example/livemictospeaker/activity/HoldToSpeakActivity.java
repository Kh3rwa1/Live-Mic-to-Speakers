package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
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
import java.util.ArrayDeque;
import java.util.Queue;
import demo.ads.GoogleAds;

public class HoldToSpeakActivity extends AppCompatActivity {
    private final RecordingSession recording = new RecordingSession();
    private final Queue<File> queue = new ArrayDeque<>();
    private MediaPlayer preview;
    private ImageView button, mic, play;
    private boolean suppressClick;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> message(granted
                    ? "Permission granted. Hold the button to record."
                    : "Microphone access is required. You can allow it in Settings."));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_hold_to_speak_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        button = findViewById(R.id.iv_start_stop_new);
        mic = findViewById(R.id.iv_mic);
        play = findViewById(R.id.iv_play);
        label(R.id.iv_back, "Back").setOnClickListener(v -> finish());
        label(R.id.iv_history, "Saved recordings").setOnClickListener(v ->
                startActivity(new Intent(this, MySavedHoldtoSpeakActivity.class)));
        play.setContentDescription("Play queued recordings");
        play.setOnClickListener(v -> { if (preview != null) stopPreview(); else playNext(); });
        mic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            if (!suppressClick) { if (recording.isRecording()) finishRecording(true, true); else startRecording(); }
        });
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: startRecording(); return true;
                case MotionEvent.ACTION_UP:
                    finishRecording(true, true);
                    suppressClick = true;
                    view.performClick();
                    suppressClick = false;
                    return true;
                case MotionEvent.ACTION_CANCEL: finishRecording(false, false); return true;
                default: return true;
            }
        });
        updateControls();
    }
    private View label(int id, String description) {
        View view = findViewById(id); view.setContentDescription(description); return view;
    }
    private void startRecording() {
        if (recording.isRecording()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return;
        }
        stopPreview();
        try { recording.start(this, new File(MyPref.creatsDirsforholdspeak(this))); }
        catch (IOException error) { message(error.getMessage()); }
        updateControls();
    }
    private void finishRecording(boolean keep, boolean notify) {
        if (!recording.isRecording()) return;
        try {
            File file = recording.stop(keep);
            if (file != null) { queue.offer(file); if (notify) message("Recording saved"); }
            else if (keep && notify) message("Recording too short. Hold for at least half a second.");
        } catch (IOException error) { message(error.getMessage()); }
        updateControls();
    }
    private void updateControls() {
        boolean active = recording.isRecording();
        button.setImageResource(active ? R.drawable.click_press_to_speak : R.drawable.click_to_speak);
        button.setContentDescription(active ? "Stop recording" : "Hold to record. Screen reader users can double-tap to start or stop.");
        mic.setImageResource(active ? R.drawable.pink_microphone : R.drawable.yellow_microphone);
        play.setEnabled(!active);
    }
    private void playNext() {
        if (recording.isRecording()) return;
        File file = queue.poll();
        if (file == null) { message("Record something first"); return; }
        stopPreview();
        MediaPlayer player = new MediaPlayer();
        preview = player;
        try {
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> { if (preview == mp) { mp.start(); play.setContentDescription("Stop playback"); } });
            player.setOnCompletionListener(mp -> { stopPreview(); if (!queue.isEmpty()) playNext(); });
            player.setOnErrorListener((mp, what, extra) -> { stopPreview(); message("Could not play recording"); return true; });
            player.prepareAsync();
        } catch (IOException | RuntimeException error) { stopPreview(); message("Could not open recording"); }
    }
    private void stopPreview() {
        if (preview != null) { preview.release(); preview = null; }
        if (play != null) play.setContentDescription("Play queued recordings");
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStop() { finishRecording(true, false); stopPreview(); super.onStop(); }
    @Override protected void onDestroy() { recording.close(); stopPreview(); super.onDestroy(); }
}
