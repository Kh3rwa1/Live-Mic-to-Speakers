package com.word.way.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import com.word.way.R;
import com.word.way.databinding.ActivityHoldToSpeakNewBinding;
import com.word.way.util.SystemBars;
import com.word.way.util.MyPref;
import com.word.way.util.ToolUi;
import com.word.way.audio.PreviewPlayer;
import com.word.way.audio.RecordingController;
import com.word.way.audio.RecordingSession;
import com.word.way.view.InteractiveWaveVisualizerView;
import com.word.way.viewmodel.HoldToSpeakViewModel;
import demo.ads.GoogleAds;
import java.io.File;
import java.util.ArrayList;

public class HoldToSpeakActivity extends AppCompatActivity {
    private ActivityHoldToSpeakNewBinding binding;
    private HoldToSpeakViewModel viewModel;
    private RecordingController<File> recording;
    private PreviewPlayer preview;
    private boolean suppressClick, visible, held;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                message(getString(granted ? R.string.tool_permission_hold : R.string.tool_permission_denied));
                if (!granted) ToolUi.permissionDenied(this);
            });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityHoldToSpeakNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(HoldToSpeakViewModel.class);
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().admobBanner(this, binding.nativeLay);

        if (binding.visualizerHold != null) {
            binding.visualizerHold.setColorTheme(InteractiveWaveVisualizerView.THEME_HOLD_TO_SPEAK);
        }
        if (binding.tvTitle != null) {
            binding.tvTitle.post(() -> {
                float textWidth = binding.tvTitle.getPaint().measureText(binding.tvTitle.getText().toString());
                if (textWidth > 0) {
                    binding.tvTitle.getPaint().setShader(new LinearGradient(
                            0, 0, textWidth, 0,
                            new int[]{0xFF111827, 0xFF111827, 0xFF0B6FD6, 0xFF4A7AB5, 0xFF8FB8DD},
                            new float[]{0.0f, 0.54f, 0.64f, 0.84f, 1.0f},
                            Shader.TileMode.CLAMP));
                    binding.tvTitle.invalidate();
                }
            });
        }
        View.OnClickListener showGuide = v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.tool_how_it_works_title)
                .setMessage(R.string.tool_how_it_works_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        if (binding.btnHowItWorks != null) {
            binding.btnHowItWorks.setOnClickListener(showGuide);
        }
        if (binding.cardHoldGuide != null) {
            binding.cardHoldGuide.setOnClickListener(showGuide);
        }
        ToolUi.button(binding.ivMic);
        if (binding.lottieVoiceWave != null) {
            binding.lottieVoiceWave.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        binding.ivBack.setOnClickListener(v -> finish());
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        binding.ivHistory.setOnClickListener(v -> startActivity(new Intent(this, MySavedHoldtoSpeakActivity.class)));
        if (state != null) {
            ArrayList<String> paths = state.getStringArrayList("queuedClips");
            if (paths != null) {
                for (String path : paths) {
                    File clip = new File(path);
                    viewModel.enqueue(clip);
                }
            }
        }
        preview = new PreviewPlayer(this, new PreviewPlayer.Listener() {
            public void onChanged() { updatePreview(); }
            public void onCompleted() { preview.stop(); if (!viewModel.isQueueEmpty() && visible) playNext(); }
            public void onError() { message(getString(R.string.tool_play_error)); }
        });
        recording = RecordingSession.controller(this, new File(MyPref.holdToSpeakDirectory(this)), new RecordingController.Listener<File>() {
            public void onStateChanged() { updateControls(); }
            public void onFinished(File file, boolean keep) {
                if (file != null) {
                    viewModel.enqueue(file);
                    viewModel.setLastSaved(file);
                    message(getString(R.string.tool_saved_message));
                } else if (keep) {
                    message(getString(R.string.tool_hold_too_short));
                }
                updatePreview();
            }
            public void onError(Exception error) { message(getString(R.string.tool_record_error)); }
        });
        binding.ivPlay.setOnClickListener(v -> {
            if (preview.wantsPlayback()) preview.pause();
            else if (preview.hasTrack()) preview.resume();
            else playNext();
        });
        if (binding.layoutMicHero != null) {
            binding.layoutMicHero.setFocusable(false);
            binding.layoutMicHero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        View.OnClickListener pushToTalkClick = v -> {
            if (!suppressClick) { if (recording.isActive()) recording.stop(true); else startRecording(); }
        };
        View.OnTouchListener pushToTalkTouch = (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    held = true;
                    view.setPressed(true);
                    if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true);
                    binding.ivMic.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start();
                    startRecording();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (held && (event.getX() < 0 || event.getY() < 0
                            || event.getX() > view.getWidth() || event.getY() > view.getHeight())) {
                        held = false;
                        view.setPressed(false);
                        binding.ivMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                        recording.stop(false);
                        if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (held) recording.stop(true);
                    held = false;
                    view.setPressed(false);
                    binding.ivMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
                    suppressClick = true;
                    view.performClick();
                    suppressClick = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    held = false;
                    view.setPressed(false);
                    binding.ivMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    recording.stop(false);
                    if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        };
        binding.ivStartStopNew.setOnClickListener(pushToTalkClick);
        binding.ivStartStopNew.setOnTouchListener(pushToTalkTouch);
        binding.ivMic.setOnClickListener(pushToTalkClick);
        binding.ivMic.setOnTouchListener(pushToTalkTouch);
        if (binding.layoutMicHero != null) {
            binding.layoutMicHero.setOnClickListener(pushToTalkClick);
            binding.layoutMicHero.setOnTouchListener(pushToTalkTouch);
        }
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
        binding.ivStartStopNew.setImageResource(active ? R.drawable.tool_stop : R.drawable.ic_mic_white);
        binding.ivStartStopNew.setEnabled(state != RecordingController.State.STOPPING && state != RecordingController.State.CLOSED);
        binding.ivStartStopNew.setContentDescription(getString(active ? R.string.tool_stop_recording : R.string.tool_hold_accessibility));
        binding.ivMic.setContentDescription(getString(active ? R.string.tool_stop_recording : R.string.tool_hold_accessibility));
        binding.ivMic.setImageResource(R.drawable.ic_home_hold_speaker);
        if (binding.visualizerHold != null) {
            binding.visualizerHold.setRecording(active);
            binding.visualizerHold.setAudioLevel(active ? 75 : 0);
        }
        if (active) {
            if (binding.lottieVoiceWave != null && !binding.lottieVoiceWave.isAnimating()) {
                binding.lottieVoiceWave.setVisibility(View.VISIBLE);
                binding.lottieVoiceWave.playAnimation();
            }
            binding.ivMic.animate().scaleX(1.06f).scaleY(1.06f).setDuration(250).start();
            binding.ivStartStopNew.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start();
        } else {
            if (binding.lottieVoiceWave != null) {
                binding.lottieVoiceWave.cancelAnimation();
                binding.lottieVoiceWave.setVisibility(View.INVISIBLE);
            }
            binding.ivMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
            binding.ivStartStopNew.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        }
        binding.tvStartStopNew.setText(state == RecordingController.State.STARTING ? R.string.tool_preparing
                : state == RecordingController.State.STOPPING ? R.string.tool_saving
                : active ? R.string.tool_hold_active : R.string.hold_to_speak_caps);
        updatePreview();
    }

    private void updatePreview() {
        if (binding.ivPlay == null) return;
        boolean active = preview != null && preview.wantsPlayback();
        binding.ivPlay.setContentDescription(getString(active ? R.string.tool_pause : R.string.tool_play_queue));
        binding.ivPlay.setImageResource(active ? R.drawable.tool_pause : R.drawable.tool_play);
        binding.ivPlay.setEnabled(recording != null && recording.getState() == RecordingController.State.IDLE
                && (!viewModel.isQueueEmpty() || (preview != null && preview.hasTrack())));
    }

    private void playNext() {
        if (recording.getState() != RecordingController.State.IDLE) return;
        File file = viewModel.pollNext();
        if (file == null) { message(getString(R.string.tool_no_recording)); updatePreview(); return; }
        preview.play(Uri.fromFile(file));
    }

    private void message(String text) { if (visible) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); visible = true; }
    // Consent is home-screen only to avoid interrupting hold-to-record.
    @Override protected void onSaveInstanceState(Bundle state) {
        ArrayList<String> paths = new ArrayList<>();
        for (File file : viewModel.getQueue()) if (file.isFile()) paths.add(file.getAbsolutePath());
        state.putStringArrayList("queuedClips", paths);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() {
        visible = false; held = false; recording.stop(true); preview.stop();
        if (binding.visualizerHold != null) {
            binding.visualizerHold.setRecording(false);
            binding.visualizerHold.setAudioLevel(0);
        }
        super.onStop();
    }
    @Override protected void onDestroy() { recording.close(); preview.close(); super.onDestroy(); }
}
