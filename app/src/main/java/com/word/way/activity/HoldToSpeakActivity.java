package com.word.way.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.Utils.MyPref;
import com.word.way.Utils.ToolUi;
import com.word.way.audio.PreviewPlayer;
import com.word.way.audio.RecordingController;
import com.word.way.audio.RecordingSession;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import demo.ads.GoogleAds;

public class HoldToSpeakActivity extends AppCompatActivity {
    private RecordingController<File> recording;
    private final Queue<File> queue = new ArrayDeque<>();
    private PreviewPlayer preview;
    private View button, play;
    private TextView feedback;
    private boolean suppressClick, visible, held;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                feedback.setText(granted ? R.string.tool_permission_hold : R.string.tool_permission_denied);
                if (!granted) ToolUi.permissionDenied(this);
            });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_hold_to_speak_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        button = findViewById(R.id.iv_start_stop_new); play = findViewById(R.id.iv_play);
        feedback = findViewById(R.id.studio_feedback);
        ToolUi.button(button); ToolUi.button(play);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.iv_history).setOnClickListener(v -> startActivity(new Intent(this, MySavedHoldtoSpeakActivity.class)));
        if (state != null && state.getStringArrayList("queuedClips") != null) {
            for (String path : state.getStringArrayList("queuedClips")) {
                File clip = new File(path);
                if (clip.isFile()) queue.offer(clip);
            }
        }
        preview = new PreviewPlayer(this, new PreviewPlayer.Listener() {
            public void onChanged() { updatePreview(); }
            public void onCompleted() { preview.stop(); if (!queue.isEmpty() && visible) playNext(); }
            public void onError() { feedback.setText(R.string.tool_play_error); }
        });
        recording = RecordingSession.controller(this, new File(MyPref.creatsDirsforholdspeak(this)), new RecordingController.Listener<File>() {
            public void onStateChanged() { updateControls(); }
            public void onLevel(int percent) { if (visible) ToolUi.level(HoldToSpeakActivity.this, percent); }
            public void onFinished(File file, boolean keep) {
                if (file != null) { queue.offer(file); feedback.setText(R.string.tool_saved_message); }
                else feedback.setText(keep ? R.string.tool_hold_too_short : R.string.studio_cancelled);
                updatePreview();
            }
            public void onError(Exception error) { feedback.setText(R.string.tool_record_error); }
        });
        play.setOnClickListener(v -> {
            if (recording.getState() != RecordingController.State.IDLE) return;
            if (preview.wantsPlayback()) preview.pause();
            else if (preview.hasTrack()) preview.resume();
            else playNext();
        });
        button.setOnClickListener(v -> {
            if (!suppressClick) { if (recording.isActive()) recording.stop(true); else startRecording(); }
        });
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    held = true; view.setPressed(true);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    startRecording(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (held && (event.getX() < 0 || event.getY() < 0 || event.getX() > view.getWidth() || event.getY() > view.getHeight())) {
                        held = false; view.setPressed(false); recording.stop(false);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (held) recording.stop(true);
                    held = false; view.setPressed(false);
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    suppressClick = true; view.performClick(); suppressClick = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    held = false; view.setPressed(false); recording.stop(false);
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
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
        ((ImageView) findViewById(R.id.studio_action_icon)).setImageResource(active ? R.drawable.tool_stop : R.drawable.tool_record);
        ToolUi.enabled(button, state != RecordingController.State.STOPPING && state != RecordingController.State.CLOSED);
        button.setContentDescription(getString(active ? R.string.tool_stop_recording : R.string.tool_hold_accessibility));
        ((TextView) findViewById(R.id.tv_start_stop_new)).setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                : state == RecordingController.State.STOPPING ? R.string.tool_saving
                : active ? R.string.tool_hold_active : R.string.tool_hold_idle);
        feedback.setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                : state == RecordingController.State.STOPPING ? R.string.tool_saving
                : active ? R.string.tool_mic_active : R.string.studio_ready);
        if (state != RecordingController.State.RECORDING) ToolUi.level(this, 0);
        updatePreview();
    }
    private void updatePreview() {
        boolean active = preview != null && preview.wantsPlayback();
        int title = active ? R.string.tool_pause : R.string.tool_play_queue;
        ((TextView) findViewById(R.id.studio_preview_label)).setText(title);
        play.setContentDescription(getString(title));
        ToolUi.enabled(play, recording != null && recording.getState() == RecordingController.State.IDLE
                && (!queue.isEmpty() || (preview != null && preview.hasTrack())));
        ((TextView) findViewById(R.id.studio_saved_detail)).setText(getResources().getQuantityString(
                R.plurals.studio_queue_count, queue.size(), queue.size()));
    }
    private void playNext() {
        if (recording.getState() != RecordingController.State.IDLE) return;
        File file;
        do { file = queue.poll(); } while (file != null && !file.isFile());
        if (file == null) { feedback.setText(R.string.tool_no_recording); updatePreview(); return; }
        preview.play(Uri.fromFile(file));
    }
    @Override protected void onStart() { super.onStart(); visible = true; }
    @Override protected void onSaveInstanceState(Bundle state) {
        ArrayList<String> paths = new ArrayList<>();
        for (File file : queue) paths.add(file.getAbsolutePath());
        state.putStringArrayList("queuedClips", paths);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() { visible = false; held = false; recording.stop(true); preview.stop(); super.onStop(); }
    @Override protected void onDestroy() { recording.close(); preview.close(); super.onDestroy(); }
}
