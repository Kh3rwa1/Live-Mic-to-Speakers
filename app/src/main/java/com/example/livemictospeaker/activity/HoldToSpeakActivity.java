package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import com.example.livemictospeaker.audio.PreviewPlayer;
import com.example.livemictospeaker.audio.RecordingController;
import com.example.livemictospeaker.audio.RecordingSession;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import demo.ads.GoogleAds;

public class HoldToSpeakActivity extends AppCompatActivity {
    private RecordingController<File> recording;
    private final Queue<File> queue = new ArrayDeque<>();
    private PreviewPlayer preview;
    private ImageView button, mic, play;
    private boolean suppressClick, visible;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> message(granted
                    ? "Permission granted. Hold the button to record."
                    : "Microphone access is required. You can allow it in Settings."));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_hold_to_speak_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        button = findViewById(R.id.iv_start_stop_new); mic = findViewById(R.id.iv_mic); play = findViewById(R.id.iv_play);
        label(R.id.iv_back, "Back").setOnClickListener(v -> finish());
        label(R.id.iv_history, "Saved recordings").setOnClickListener(v -> startActivity(new Intent(this, MySavedHoldtoSpeakActivity.class)));
        preview = new PreviewPlayer(this, new PreviewPlayer.Listener() {
            public void onChanged() { play.setContentDescription(preview != null && preview.wantsPlayback() ? "Pause playback" : "Play queued recordings"); }
            public void onCompleted() { preview.stop(); if (!queue.isEmpty() && visible) playNext(); }
            public void onError() { message("Could not play recording. Check the file or other audio apps."); }
        });
        recording = RecordingSession.controller(this, new File(MyPref.creatsDirsforholdspeak(this)), new RecordingController.Listener<File>() {
            public void onStateChanged() { updateControls(); }
            public void onFinished(File file, boolean keep) {
                if (file != null) { queue.offer(file); message("Recording saved"); }
                else if (keep) message("Recording too short or cancelled before starting. Hold for at least half a second.");
            }
            public void onError(Exception error) { message(error.getMessage()); }
        });
        play.setContentDescription("Play queued recordings");
        play.setOnClickListener(v -> {
            if (preview.wantsPlayback()) preview.pause();
            else if (preview.hasTrack()) preview.resume();
            else playNext();
        });
        mic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            if (!suppressClick) { if (recording.isActive()) recording.stop(true); else startRecording(); }
        });
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: startRecording(); return true;
                case MotionEvent.ACTION_UP:
                    recording.stop(true); suppressClick = true; view.performClick(); suppressClick = false; return true;
                case MotionEvent.ACTION_CANCEL: recording.stop(false); return true;
                default: return true;
            }
        });
        updateControls();
    }
    private View label(int id, String description) { View view = findViewById(id); view.setContentDescription(description); return view; }
    private void startRecording() {
        if (recording.getState() != RecordingController.State.IDLE) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return;
        }
        preview.stop(); recording.start();
    }
    private void updateControls() {
        boolean active = recording.isActive();
        button.setImageResource(active ? R.drawable.click_press_to_speak : R.drawable.click_to_speak);
        button.setEnabled(recording.getState() != RecordingController.State.STOPPING);
        button.setContentDescription(active ? "Stop recording" : "Hold to record. Screen reader users can double-tap to start or stop.");
        mic.setImageResource(active ? R.drawable.pink_microphone : R.drawable.yellow_microphone);
        play.setEnabled(recording.getState() == RecordingController.State.IDLE);
    }
    private void playNext() {
        if (recording.getState() != RecordingController.State.IDLE) return;
        File file = queue.poll();
        if (file == null) { message("Record something first"); return; }
        preview.play(Uri.fromFile(file));
    }
    private void message(String text) { if (visible) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); visible = true; }
    @Override protected void onStop() { visible = false; recording.stop(true); preview.stop(); super.onStop(); }
    @Override protected void onDestroy() { recording.close(); preview.close(); super.onDestroy(); }
}
