package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
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
            new ActivityResultContracts.RequestPermission(), granted -> message(getString(granted
                    ? R.string.tool_permission_hold : R.string.tool_permission_denied)));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_hold_to_speak_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        button = findViewById(R.id.iv_start_stop_new); mic = findViewById(R.id.iv_mic); play = findViewById(R.id.iv_play);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.iv_history).setOnClickListener(v -> startActivity(new Intent(this, MySavedHoldtoSpeakActivity.class)));
        preview = new PreviewPlayer(this, new PreviewPlayer.Listener() {
            public void onChanged() {
                boolean active = preview != null && preview.wantsPlayback();
                play.setContentDescription(getString(active ? R.string.tool_pause : R.string.tool_play_queue));
                play.setImageResource(active ? R.drawable.tool_pause : R.drawable.tool_play);
            }
            public void onCompleted() { preview.stop(); if (!queue.isEmpty() && visible) playNext(); }
            public void onError() { message(getString(R.string.tool_play_error)); }
        });
        recording = RecordingSession.controller(this, new File(MyPref.creatsDirsforholdspeak(this)), new RecordingController.Listener<File>() {
            public void onStateChanged() { updateControls(); }
            public void onFinished(File file, boolean keep) {
                if (file != null) { queue.offer(file); message(getString(R.string.tool_saved_message)); }
                else if (keep) message(getString(R.string.tool_hold_too_short));
            }
            public void onError(Exception error) { message(getString(R.string.tool_record_error)); }
        });
        play.setOnClickListener(v -> {
            if (preview.wantsPlayback()) preview.pause();
            else if (preview.hasTrack()) preview.resume();
            else playNext();
        });
        mic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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
    private void startRecording() {
        if (recording.getState() != RecordingController.State.IDLE) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return;
        }
        preview.stop(); recording.start();
    }
    private void updateControls() {
        RecordingController.State state = recording.getState();
        boolean active = recording.isActive();
        button.setImageResource(active ? R.drawable.tool_stop : R.drawable.tool_record);
        button.setEnabled(state != RecordingController.State.STOPPING && state != RecordingController.State.CLOSED);
        button.setContentDescription(getString(active ? R.string.tool_stop_recording : R.string.tool_hold_accessibility));
        mic.setImageResource(R.drawable.tool_mic);
        ((TextView) findViewById(R.id.tv_start_stop_new)).setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                : state == RecordingController.State.STOPPING ? R.string.tool_saving
                : active ? R.string.tool_hold_active : R.string.tool_hold_idle);
        play.setEnabled(state == RecordingController.State.IDLE);
    }
    private void playNext() {
        if (recording.getState() != RecordingController.State.IDLE) return;
        File file = queue.poll();
        if (file == null) { message(getString(R.string.tool_no_recording)); return; }
        preview.play(Uri.fromFile(file));
    }
    private void message(String text) { if (visible) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); visible = true; }
    @Override protected void onStop() { visible = false; recording.stop(true); preview.stop(); super.onStop(); }
    @Override protected void onDestroy() { recording.close(); preview.close(); super.onDestroy(); }
}
