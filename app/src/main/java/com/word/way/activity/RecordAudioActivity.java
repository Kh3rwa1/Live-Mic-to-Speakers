package com.word.way.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;
import com.word.way.R;
import com.word.way.databinding.ActivityRecordAudioNewBinding;
import com.word.way.util.SystemBars;
import com.word.way.util.MyPref;
import com.word.way.util.ToolUi;
import com.word.way.audio.PreviewPlayer;
import com.word.way.audio.RecordingController;
import com.word.way.audio.RecordingSession;
import com.word.way.view.InteractiveWaveVisualizerView;
import com.word.way.viewmodel.RecordAudioViewModel;
import demo.ads.GoogleAds;
import java.io.File;
import java.util.Locale;

public class RecordAudioActivity extends AppCompatActivity {
    private ActivityRecordAudioNewBinding binding;
    private RecordAudioViewModel viewModel;
    private RecordingController<File> recording;
    private PreviewPlayer preview;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible;

    private static final class TimerTick implements Runnable {
        private final java.lang.ref.WeakReference<RecordAudioActivity> owner;
        TimerTick(RecordAudioActivity activity) { owner = new java.lang.ref.WeakReference<>(activity); }
        @Override public void run() {
            RecordAudioActivity activity = owner.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (activity.recording == null
                    || activity.recording.getState() != RecordingController.State.RECORDING) return;
            long startedAt = activity.viewModel.getStartedAt();
            long seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000;
            if (activity.binding.tvTimer != null) {
                activity.binding.tvTimer.setText(String.format(Locale.getDefault(),
                        "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
            }
            activity.handler.postDelayed(this, 250);
        }
    }
    private final Runnable tick = new TimerTick(this);
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                message(getString(granted ? R.string.tool_permission_record : R.string.tool_permission_denied));
                if (!granted) ToolUi.permissionDenied(this);
            });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityRecordAudioNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(RecordAudioViewModel.class);
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().admobBanner(this, binding.nativeLay);

        if (binding.visualizerRecord != null) {
            binding.visualizerRecord.setColorTheme(InteractiveWaveVisualizerView.THEME_HOLD_TO_SPEAK);
        }
        if (binding.tvTitle != null) {
            // Locale-safe full-text gradient; no hardcoded English word widths.
            binding.tvTitle.post(() -> {
                float textWidth = binding.tvTitle.getPaint().measureText(binding.tvTitle.getText().toString());
                if (textWidth > 0) {
                    binding.tvTitle.getPaint().setShader(new LinearGradient(
                            0, 0, textWidth, 0,
                            new int[]{0xFF111827, 0xFF1A9BF0, 0xFF0B6FD6, 0xFF8FB8DD},
                            new float[]{0.0f, 0.45f, 0.75f, 1.0f},
                            Shader.TileMode.CLAMP));
                    binding.tvTitle.invalidate();
                }
            });
        }
        TextView timer = binding.tvTimer;
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(timer, 20, 44, 1, TypedValue.COMPLEX_UNIT_SP);
        if (binding.lottieRecordWave != null) {
            binding.lottieRecordWave.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        binding.ivBack.setOnClickListener(v -> finish());
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        if (binding.ivMic != null) {
            binding.ivMic.setOnClickListener(v -> binding.ivStartStopNew.performClick());
        }
        binding.ivHistory.setOnClickListener(v -> startActivity(new Intent(this, MySavedAnnounceActivity.class)));

        if (state != null && state.getString("lastSaved") != null) {
            viewModel.setLastSaved(new File(state.getString("lastSaved")));
        }
        if (state != null && state.getLong("startedAt", 0) != 0) {
            viewModel.setStartedAt(state.getLong("startedAt", 0));
        }

        preview = new PreviewPlayer(this, new PreviewPlayer.Listener() {
            public void onChanged() { updatePreview(); }
            public void onCompleted() { preview.stop(); }
            public void onError() { message(getString(R.string.tool_play_error)); }
        });
        recording = RecordingSession.controller(this, new File(MyPref.recordingsDirectory(this)), new RecordingController.Listener<File>() {
            public void onStateChanged() { updateControls(); }
            public void onFinished(File file, boolean keep) {
                if (file != null) {
                    viewModel.setLastSaved(file);
                    message(getString(R.string.tool_saved_message));
                } else if (keep) {
                    message(getString(R.string.tool_too_short));
                }
                updatePreview();
            }
            public void onError(Exception error) { message(getString(R.string.tool_record_error)); }
        });
        binding.ivStartStopNew.setOnClickListener(v -> {
            if (recording.isActive()) { recording.stop(true); return; }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.RECORD_AUDIO); return;
            }
            preview.stop();
            viewModel.setStartedAt(0);
            recording.start();
        });
        binding.ivPlay.setOnClickListener(v -> {
            if (preview.wantsPlayback()) { preview.pause(); return; }
            if (preview.hasTrack()) { preview.resume(); return; }
            File lastSaved = viewModel.getLastSaved();
            if (lastSaved == null || !lastSaved.isFile()) { message(getString(R.string.tool_no_recording)); return; }
            preview.play(Uri.fromFile(lastSaved));
        });
        updateControls();
        updatePreview();
    }

    private void updateControls() {
        RecordingController.State state = recording.getState();
        boolean active = recording.isActive();
        binding.ivStartStopNew.setImageResource(active ? R.drawable.tool_stop : R.drawable.ic_record_circle_white);
        binding.ivStartStopNew.setEnabled(state != RecordingController.State.STOPPING && state != RecordingController.State.CLOSED);
        binding.ivStartStopNew.setContentDescription(getString(active ? R.string.tool_stop_recording : R.string.tool_start_recording));
        if (binding.visualizerRecord != null) {
            binding.visualizerRecord.setRecording(active);
        }
        if (binding.tvTimerStatus != null) {
            binding.tvTimerStatus.setText(active ? R.string.tool_recording_in_progress : R.string.tool_ready_to_record);
        }
        if (binding.tvRecordStatusDesc != null) {
            binding.tvRecordStatusDesc.setText(active ? R.string.tool_record_status_saving_hint : R.string.tool_record_status_help);
        }
        if (active) {
            if (binding.lottieRecordWave != null && !binding.lottieRecordWave.isAnimating()) {
                binding.lottieRecordWave.setVisibility(View.VISIBLE);
                binding.lottieRecordWave.playAnimation();
            }
            binding.ivStartStopNew.animate().scaleX(1.08f).scaleY(1.08f).setDuration(250).start();
        } else {
            if (binding.lottieRecordWave != null) {
                binding.lottieRecordWave.pauseAnimation();
                binding.lottieRecordWave.setVisibility(View.VISIBLE);
            }
            binding.ivStartStopNew.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        }
        binding.tvStartStopNew.setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                : state == RecordingController.State.STOPPING ? R.string.tool_saving : active ? R.string.tool_stop : R.string.tool_start);
        if (binding.studioFeedback != null) {
            binding.studioFeedback.setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                    : state == RecordingController.State.STOPPING ? R.string.tool_saving
                    : state == RecordingController.State.RECORDING ? R.string.tool_record_status_recording : R.string.studio_ready);
        }
        if (state == RecordingController.State.RECORDING && viewModel.getStartedAt() == 0) {
            viewModel.setStartedAt(SystemClock.elapsedRealtime());
            handler.post(tick);
        }
        if (state != RecordingController.State.RECORDING) handler.removeCallbacks(tick);
        updatePreview();
    }

    private void updatePreview() {
        if (binding.ivPlay == null) return;
        boolean active = preview != null && preview.wantsPlayback();
        binding.ivPlay.setImageResource(active ? R.drawable.tool_pause : R.drawable.tool_play);
        binding.ivPlay.setContentDescription(getString(active ? R.string.tool_pause : R.string.tool_play_last));
        File lastSaved = viewModel.getLastSaved();
        binding.ivPlay.setEnabled(recording != null && recording.getState() == RecordingController.State.IDLE
                && ((lastSaved != null && lastSaved.isFile()) || (preview != null && preview.hasTrack())));
    }

    private void message(String text) { if (visible) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); visible = true; }
    // Consent is home-screen only to avoid interrupting recording.
    @Override protected void onSaveInstanceState(Bundle state) {
        File lastSaved = viewModel.getLastSaved();
        if (lastSaved != null) state.putString("lastSaved", lastSaved.getAbsolutePath());
        if (viewModel.getStartedAt() != 0) state.putLong("startedAt", viewModel.getStartedAt());
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() {
        visible = false; recording.stop(true); preview.stop(); handler.removeCallbacks(tick); super.onStop();
    }
    @Override protected void onDestroy() { recording.close(); preview.close(); handler.removeCallbacks(tick); super.onDestroy(); }
}
