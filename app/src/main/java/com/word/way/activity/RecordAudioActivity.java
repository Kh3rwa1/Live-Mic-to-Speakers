package com.word.way.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.Utils.MyPref;
import com.word.way.audio.PreviewPlayer;
import com.word.way.audio.RecordingController;
import com.word.way.audio.RecordingSession;
import android.view.View;
import java.io.File;
import java.util.Locale;
import demo.ads.GoogleAds;

public class RecordAudioActivity extends AppCompatActivity {
    private RecordingController<File> recording;
    private PreviewPlayer preview;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView toggle, play;
    private TextView timer, label;
    private com.airbnb.lottie.LottieAnimationView lottieRecordWave;
    private File lastSaved;
    private long startedAt;
    private boolean visible;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (recording.getState() != RecordingController.State.RECORDING) return;
            long seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000;
            timer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
            handler.postDelayed(this, 250);
        }
    };
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> message(getString(granted
                    ? R.string.tool_permission_record : R.string.tool_permission_denied)));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_record_audio_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        toggle = findViewById(R.id.iv_start_stop_new); play = findViewById(R.id.iv_play);
        timer = findViewById(R.id.tv_timer); label = findViewById(R.id.tv_start_stop_new);
        lottieRecordWave = findViewById(R.id.lottie_record_wave);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.iv_history).setOnClickListener(v -> startActivity(new Intent(this, MySavedAnnounceActivity.class)));
        if (state != null && state.getString("lastSaved") != null) lastSaved = new File(state.getString("lastSaved"));
        preview = new PreviewPlayer(this, new PreviewPlayer.Listener() {
            public void onChanged() { updatePreview(); }
            public void onCompleted() { preview.stop(); }
            public void onError() { message(getString(R.string.tool_play_error)); }
        });
        recording = RecordingSession.controller(this, new File(MyPref.creatsDirsforApp(this)), new RecordingController.Listener<File>() {
            public void onStateChanged() { updateControls(); }
            public void onFinished(File file, boolean keep) {
                if (file != null) { lastSaved = file; message(getString(R.string.tool_saved_message)); }
                else if (keep) message(getString(R.string.tool_too_short));
            }
            public void onError(Exception error) { message(getString(R.string.tool_record_error)); }
        });
        toggle.setOnClickListener(v -> {
            if (recording.isActive()) { recording.stop(true); return; }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.RECORD_AUDIO); return;
            }
            preview.stop(); startedAt = 0; recording.start();
        });
        play.setOnClickListener(v -> {
            if (preview.wantsPlayback()) { preview.pause(); return; }
            if (preview.hasTrack()) { preview.resume(); return; }
            if (lastSaved == null || !lastSaved.isFile()) { message(getString(R.string.tool_no_recording)); return; }
            preview.play(Uri.fromFile(lastSaved));
        });
        updateControls(); updatePreview();
    }
    private void updateControls() {
        RecordingController.State state = recording.getState();
        boolean active = recording.isActive();
        toggle.setImageResource(active ? R.drawable.tool_stop : R.drawable.tool_record);
        toggle.setEnabled(state != RecordingController.State.STOPPING && state != RecordingController.State.CLOSED);
        toggle.setContentDescription(getString(active ? R.string.tool_stop_recording : R.string.tool_start_recording));
        if (active) {
            if (lottieRecordWave != null && !lottieRecordWave.isAnimating()) {
                lottieRecordWave.setVisibility(View.VISIBLE);
                lottieRecordWave.playAnimation();
            }
            toggle.animate().scaleX(1.08f).scaleY(1.08f).setDuration(250).start();
        } else {
            if (lottieRecordWave != null) {
                lottieRecordWave.cancelAnimation();
                lottieRecordWave.setVisibility(View.GONE);
            }
            toggle.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        }
        label.setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                : state == RecordingController.State.STOPPING ? R.string.tool_saving : active ? R.string.tool_stop : R.string.tool_start);
        play.setEnabled(state == RecordingController.State.IDLE);
        if (state == RecordingController.State.RECORDING && startedAt == 0) {
            startedAt = SystemClock.elapsedRealtime(); handler.post(tick);
        }
        if (state != RecordingController.State.RECORDING) handler.removeCallbacks(tick);
    }
    private void updatePreview() {
        boolean active = preview != null && preview.wantsPlayback();
        play.setImageResource(active ? R.drawable.tool_pause : R.drawable.tool_play);
        play.setContentDescription(getString(active ? R.string.tool_pause : R.string.tool_play_last));
    }
    private void message(String text) { if (visible) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); visible = true; }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (lastSaved != null) state.putString("lastSaved", lastSaved.getAbsolutePath());
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() {
        visible = false; recording.stop(true); preview.stop(); handler.removeCallbacks(tick); super.onStop();
    }
    @Override protected void onDestroy() { recording.close(); preview.close(); handler.removeCallbacks(tick); super.onDestroy(); }
}
