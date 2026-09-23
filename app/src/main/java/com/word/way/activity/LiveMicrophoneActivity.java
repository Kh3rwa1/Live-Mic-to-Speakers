package com.word.way.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import com.word.way.R;
import com.word.way.databinding.ActivityLiveMicrophoneNewBinding;
import com.word.way.util.SystemBars;
import com.word.way.util.MyPref;
import com.word.way.util.ToolUi;
import com.word.way.audio.AndroidAudioSession;
import com.word.way.audio.AudioSessionRunner;
import com.word.way.audio.LiveAudioFailure;
import com.word.way.viewmodel.LiveMicrophoneViewModel;
import demo.ads.GoogleAds;

public class LiveMicrophoneActivity extends AppCompatActivity {
    private ActivityLiveMicrophoneNewBinding binding;
    private LiveMicrophoneViewModel viewModel;
    private AudioSessionRunner runner;
    private boolean requested, visible;
    private AlertDialog startDialog;
    private MyPref prefs;
    /** Monitoring gain 0..1, read by the audio worker and updated live by the slider. */
    private volatile float liveGain = 0.8f;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Toast.makeText(this, granted ? R.string.quality_mic_permission_granted
                        : R.string.quality_mic_permission_denied, Toast.LENGTH_LONG).show();
                if (!granted) ToolUi.permissionDenied(this);
            });
    private final ActivityResultLauncher<String> bluetoothPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                // Optional: Bluetooth names degrade to system output when denied.
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLiveMicrophoneNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(LiveMicrophoneViewModel.class);
        SystemBars.applyEdgeToEdge(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        GoogleAds.getInstance().admobBanner(this, binding.nativeLay);

        if (binding.tvTitle != null) {
            binding.tvTitle.post(() -> {
                float textWidth = binding.tvTitle.getPaint().measureText(binding.tvTitle.getText().toString());
                if (textWidth > 0) {
                    binding.tvTitle.getPaint().setShader(new LinearGradient(
                            0, 0, textWidth, 0,
                            new int[]{0xFF111827, 0xFF111827, 0xFF1A9BF0, 0xFF0B6FD6, 0xFF4A7AB5, 0xFF8FB8DD},
                            new float[]{0.0f, 0.28f, 0.35f, 0.58f, 0.82f, 1.0f},
                            Shader.TileMode.CLAMP));
                    binding.tvTitle.invalidate();
                }
            });
        }
        setupGainControl();
        ToolUi.button(binding.ivMic);
        if (binding.lottieMicPulse != null) {
            binding.lottieMicPulse.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (binding.lottieSoundwave != null) {
            binding.lottieSoundwave.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        binding.ivBack.setOnClickListener(v -> finish());
        if (binding.ivRouteToggle != null) {
            binding.ivRouteToggle.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    showBluetoothRationale();
                } else {
                    Toast.makeText(this, R.string.quality_safety_note, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        if (binding.layoutMicHero != null) {
            binding.layoutMicHero.setFocusable(false);
            binding.layoutMicHero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        View.OnClickListener micTrigger = v -> binding.ivStartStopNew.performClick();
        binding.ivMic.setOnClickListener(micTrigger);
        if (binding.layoutMicHero != null) binding.layoutMicHero.setOnClickListener(micTrigger);
        View.OnTouchListener micTouchFeedback = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    binding.ivMic.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100).start();
                    return true;
                case MotionEvent.ACTION_UP:
                    binding.ivMic.animate().scaleX(requested ? 1.05f : 1.0f).scaleY(requested ? 1.05f : 1.0f).setDuration(160).start();
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    binding.ivMic.animate().scaleX(requested ? 1.05f : 1.0f).scaleY(requested ? 1.05f : 1.0f).setDuration(160).start();
                    return true;
                default:
                    return false;
            }
        };
        binding.ivMic.setOnTouchListener(micTouchFeedback);
        if (binding.layoutMicHero != null) binding.layoutMicHero.setOnTouchListener(micTouchFeedback);

        runner = new AudioSessionRunner(ticket -> new AndroidAudioSession(getApplicationContext(),
                (peak, route) -> runOnUiThread(() -> {
                    if (visible && requested && runner.isCurrent(ticket)) {
                        binding.tvStartStopNew.setText(getString(R.string.quality_mic_meter, route, peak));
                        renderMeter(peak, route);
                    }
                }), () -> runOnUiThread(() -> {
                    if (runner.isCurrent(ticket)) {
                        stopMic();
                        if (binding.studioFeedback != null) binding.studioFeedback.setText(R.string.quality_mic_interrupted);
                        Toast.makeText(this, R.string.quality_mic_interrupted, Toast.LENGTH_LONG).show();
                    }
                }), () -> liveGain), new AudioSessionRunner.Listener() {
            @Override public void onStarted(long generation) {
                runOnUiThread(() -> {
                    if (visible && runner.isCurrent(generation)) {
                        binding.tvStartStopNew.setText(R.string.quality_mic_on);
                        if (binding.studioFeedback != null) binding.studioFeedback.setText(R.string.tool_mic_active);
                    }
                });
            }
            @Override public void onError(long generation, Exception error) {
                runOnUiThread(() -> {
                    if (runner.isCurrent(generation)) showAudioError(error);
                });
            }
        });

        binding.ivStartStopNew.setOnClickListener(v -> {
            if (requested) { stopMic(); return; }
            if (startDialog != null && startDialog.isShowing()) return;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO); return;
            }
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                showBluetoothRationale();
            }
            startDialog = new AlertDialog.Builder(this).setTitle(R.string.quality_feedback_title)
                    .setMessage(R.string.quality_feedback_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.quality_start, (dialog, which) -> {
                        if (!visible || requested) return;
                        requested = true;
                        binding.ivStartStopNew.setContentDescription(getString(R.string.quality_stop_microphone));
                        binding.ivMic.setContentDescription(getString(R.string.quality_stop_microphone));
                        binding.ivStartStopNew.setImageResource(R.drawable.tool_stop);
                        binding.tvStartStopNew.setText(R.string.quality_mic_starting);
                        if (binding.visualizerLive != null) {
                            binding.visualizerLive.setRecording(true);
                        }
                        if (binding.lottieMicPulse != null) {
                            binding.lottieMicPulse.setVisibility(View.VISIBLE);
                            binding.lottieMicPulse.playAnimation();
                        }
                        if (binding.lottieSoundwave != null) {
                            binding.lottieSoundwave.setVisibility(View.VISIBLE);
                            binding.lottieSoundwave.playAnimation();
                        }
                        binding.ivMic.animate().scaleX(1.05f).scaleY(1.05f).setDuration(400).start();
                        runner.start();
                    }).create();
            startDialog.setOnDismissListener(dialog -> startDialog = null);
            startDialog.show();
        });
        stopMic();
    }

    private void showBluetoothRationale() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_rationale_title)
                .setMessage(R.string.bluetooth_rationale_message)
                .setPositiveButton(R.string.quality_start, (dialog, which) -> {
                    try {
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
                    } catch (RuntimeException ignored) { }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /** Accessible monitoring-gain slider; the value persists and applies to a running session. */
    private void setupGainControl() {
        prefs = new MyPref(this);
        float savedGain = prefs.getPref(MyPref.LiveMonitoringGain, 0.8f);
        if (viewModel.getLiveGain() == 0.8f && savedGain != 0.8f) {
            viewModel.setLiveGain(savedGain);
        }
        liveGain = clampGain(viewModel.getLiveGain());
        SeekBar seek = binding.seekGain;
        if (seek == null) { updateGainLabel(); return; }
        seek.setMax(100);
        seek.setProgress(Math.round(liveGain * 100f));
        updateGainLabel();
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                liveGain = clampGain(progress / 100f);
                viewModel.setLiveGain(liveGain);
                updateGainLabel();
                if (fromUser && prefs != null) prefs.setPref(MyPref.LiveMonitoringGain, liveGain);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private void updateGainLabel() {
        if (binding.tvGainValue != null) {
            binding.tvGainValue.setText(getString(R.string.live_gain_value, Math.round(liveGain * 100f)));
        }
    }

    private static float clampGain(float gain) {
        return Math.max(0f, Math.min(1f, gain));
    }

    private void stopMic() {
        requested = false;
        if (runner != null) runner.stop();
        binding.ivStartStopNew.setContentDescription(getString(R.string.quality_start_microphone));
        binding.ivMic.setContentDescription(getString(R.string.quality_start_microphone));
        binding.ivStartStopNew.setImageResource(R.drawable.ic_mic_white);
        binding.ivMic.setImageResource(R.drawable.ic_home_live_mic);
        binding.ivMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
        if (binding.visualizerLive != null) {
            binding.visualizerLive.setRecording(false);
            binding.visualizerLive.setAudioLevel(0);
        }
        if (binding.lottieMicPulse != null) {
            binding.lottieMicPulse.cancelAnimation();
            binding.lottieMicPulse.setVisibility(View.INVISIBLE);
        }
        if (binding.lottieSoundwave != null) {
            binding.lottieSoundwave.pauseAnimation();
            binding.lottieSoundwave.setVisibility(View.VISIBLE);
        }
        binding.tvStartStopNew.setText(R.string.quality_mic_off);
        if (binding.studioFeedback != null) binding.studioFeedback.setText(R.string.studio_ready);
        ToolUi.level(this, 0);
        if (binding.studioOutputRoute != null) binding.studioOutputRoute.setText(R.string.studio_output_idle);
    }

    void showAudioError(Exception error) {
        stopMic();
        int message;
        switch (LiveAudioFailure.reasonOf(error)) {
            case PERMISSION: message = R.string.live_error_permission; break;
            case SERVICE_UNAVAILABLE: message = R.string.live_error_service; break;
            case FOCUS_UNAVAILABLE: message = R.string.live_error_focus; break;
            case UNSUPPORTED_CONFIGURATION: message = R.string.live_error_configuration; break;
            case MICROPHONE_UNAVAILABLE: message = R.string.live_error_microphone; break;
            case READ_FAILED: message = R.string.live_error_input; break;
            case OUTPUT_FAILED: message = R.string.live_error_output; break;
            case FEEDBACK_DETECTED: message = R.string.live_error_feedback; break;
            case CANCELLED: message = R.string.live_error_cancelled; break;
            default: message = R.string.quality_mic_error;
        }
        if (binding.studioFeedback != null) binding.studioFeedback.setText(message);
    }

    void renderMeter(int peak, String route) {
        viewModel.setLastPeak(peak);
        viewModel.setLastRoute(route);
        ToolUi.level(this, peak);
        if (binding.visualizerLive != null) {
            binding.visualizerLive.setAudioLevel(peak);
        }
        if (binding.studioOutputRoute != null) {
            binding.studioOutputRoute.setText(getString(R.string.studio_output_route, route));
        }
    }

    @Override protected void onResume() { super.onResume(); visible = true; }
    // No UMP consent request here: consent is gathered on home screens so no privacy form
    // can interrupt a live microphone session. Banners still render once consent allows ads.
    @Override protected void onPause() {
        visible = false;
        if (startDialog != null) startDialog.dismiss();
        stopMic(); super.onPause();
    }
    @Override protected void onDestroy() { if (runner != null) runner.close(); super.onDestroy(); }
}
