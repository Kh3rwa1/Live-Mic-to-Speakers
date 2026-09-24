package com.word.way.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
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
    public static final String TAG_SAFETY_DIALOG = "FeedbackSafetyDialog";

    private ActivityLiveMicrophoneNewBinding binding;
    private LiveMicrophoneViewModel viewModel;
    private AudioSessionRunner runner;
    private boolean requested, visible;
    private MyPref prefs;
    private static Boolean testIsBuiltinSpeaker = null;
    private static Boolean testIsBluetoothOutput = null;
    private boolean bluetoothRequested = false;

    /** Monitoring gain 0..1, read by the audio worker and updated live by the slider. */
    private volatile float liveGain = 0.8f;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Toast.makeText(this, granted ? R.string.quality_mic_permission_granted
                        : R.string.quality_mic_permission_denied, Toast.LENGTH_LONG).show();
                if (!granted) ToolUi.permissionDenied(this);
            });
    public static final String TAG_BT_RATIONALE_DIALOG = "BluetoothRationaleDialog";

    private final ActivityResultLauncher<String> bluetoothPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (prefs == null) prefs = new MyPref(this);
                prefs.setBoolean(MyPref.BT_CONNECT_ASKED, true);
            });

    public static class FeedbackSafetyDialogFragment extends androidx.fragment.app.DialogFragment {
        @androidx.annotation.NonNull
        @Override
        public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
            androidx.fragment.app.FragmentActivity activity = requireActivity();
            View view = LayoutInflater.from(activity).inflate(R.layout.dialog_feedback_safety, null);
            CheckBox checkBox = view.findViewById(R.id.safety_dont_show_again);
            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.quality_feedback_title)
                    .setView(view)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.quality_start, (dialog, which) -> {
                        if (checkBox != null && checkBox.isChecked()) {
                            new MyPref(requireContext()).setBoolean(MyPref.SAFETY_ACK_SPEAKER, true);
                        }
                        if (getActivity() instanceof LiveMicrophoneActivity) {
                            ((LiveMicrophoneActivity) getActivity()).confirmStart();
                        }
                    })
                    .create();
        }
    }

    public static class BluetoothRationaleDialogFragment extends androidx.fragment.app.DialogFragment {
        @androidx.annotation.NonNull
        @Override
        public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
            androidx.fragment.app.FragmentActivity activity = requireActivity();
            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.bluetooth_rationale_title)
                    .setMessage(R.string.bluetooth_rationale_message)
                    .setPositiveButton(R.string.quality_start, (dialog, which) -> {
                        if (getActivity() instanceof LiveMicrophoneActivity) {
                            ((LiveMicrophoneActivity) getActivity()).requestBluetoothPermission();
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .create();
        }
    }

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
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            if (binding.studioOutputRoute != null) {
                binding.studioOutputRoute.setOnLongClickListener(v -> {
                    showDiagnosticsDialog();
                    return true;
                });
            }
        }
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        if (binding.layoutMicHero != null) {
            binding.layoutMicHero.setFocusable(false);
            binding.layoutMicHero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        View.OnClickListener micTrigger = v -> handleStartStop();
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
                    viewModel.setStarting(false);
                    viewModel.setRunning(true);
                    if (visible && runner.isCurrent(generation)) {
                        binding.tvStartStopNew.setText(R.string.quality_mic_on);
                        if (binding.studioFeedback != null) binding.studioFeedback.setText(R.string.tool_mic_active);
                        maybeRequestBluetoothPermission();
                    }
                });
            }
            @Override public void onError(long generation, Exception error) {
                runOnUiThread(() -> {
                    viewModel.setStarting(false);
                    viewModel.setRunning(false);
                    if (runner.isCurrent(generation)) showAudioError(error);
                });
            }
        });

        binding.ivStartStopNew.setOnClickListener(v -> handleStartStop());
        stopMic();
    }

    private void handleStartStop() {
        if (requested || viewModel.isRunning()) {
            stopMic();
            return;
        }
        if (viewModel.isStarting()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        if (prefs == null) prefs = new MyPref(this);
        if (isBuiltinSpeakerOutput() && !prefs.getBoolean(MyPref.SAFETY_ACK_SPEAKER, false)) {
            showSafetyDialog();
            return;
        }
        startMicSession();
    }

    public void confirmStart() {
        androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_SAFETY_DIALOG);
        if (fragment instanceof androidx.fragment.app.DialogFragment) {
            ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
        }
        startMicSession();
    }

    private synchronized void startMicSession() {
        if (isFinishing() || isDestroyed() || requested || viewModel.isStarting() || viewModel.isRunning()) {
            return;
        }
        viewModel.setStarting(true);
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
    }

    private void showSafetyDialog() {
        if (!visible || isFinishing() || isDestroyed()) return;
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG_SAFETY_DIALOG) != null) return;
        try {
            new FeedbackSafetyDialogFragment().showNow(fm, TAG_SAFETY_DIALOG);
        } catch (IllegalStateException e) {
            new FeedbackSafetyDialogFragment().show(fm, TAG_SAFETY_DIALOG);
        }
    }

    private void showBluetoothRationale() {
        if (!visible || isFinishing() || isDestroyed()) return;
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG_BT_RATIONALE_DIALOG) != null) return;
        try {
            new BluetoothRationaleDialogFragment().showNow(fm, TAG_BT_RATIONALE_DIALOG);
        } catch (IllegalStateException e) {
            new BluetoothRationaleDialogFragment().show(fm, TAG_BT_RATIONALE_DIALOG);
        }
    }

    private void showDiagnosticsDialog() {
        com.word.way.audio.AudioDiagnostics diag = com.word.way.audio.AudioDiagnostics.get();
        String text = diag.toFormattedString();
        new AlertDialog.Builder(this)
                .setTitle("Audio Diagnostics")
                .setMessage(text)
                .setPositiveButton(android.R.string.copy, (dialog, which) -> {
                    android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) {
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Audio Diagnostics", text));
                        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void requestBluetoothPermission() {
        try {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
        } catch (RuntimeException ignored) { }
    }

    private void maybeRequestBluetoothPermission() {
        if (Build.VERSION.SDK_INT < 31) return;
        if (prefs == null) prefs = new MyPref(this);
        if (prefs.getBoolean(MyPref.BT_CONNECT_ASKED, false)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (isBluetoothOutputConnected()) {
            prefs.setBoolean(MyPref.BT_CONNECT_ASKED, true);
            bluetoothRequested = true;
            showBluetoothRationale();
        }
    }

    public boolean isBluetoothOutputConnected() {
        if (testIsBluetoothOutput != null) return testIsBluetoothOutput;
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return false;
        try {
            AudioDeviceInfo[] devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    return true;
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    public boolean isHeadphonesOrHeadsetConnected() {
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return false;
        try {
            AudioDeviceInfo[] devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || type == AudioDeviceInfo.TYPE_USB_HEADSET
                        || type == AudioDeviceInfo.TYPE_USB_DEVICE
                        || type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                        || type == AudioDeviceInfo.TYPE_LINE_ANALOG
                        || type == AudioDeviceInfo.TYPE_LINE_DIGITAL
                        || type == AudioDeviceInfo.TYPE_AUX_LINE) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    public boolean isBuiltinSpeakerOutput() {
        if (testIsBuiltinSpeaker != null) return testIsBuiltinSpeaker;
        if (isBluetoothOutputConnected() || isHeadphonesOrHeadsetConnected()) {
            return false;
        }
        return true;
    }

    @androidx.annotation.VisibleForTesting
    public static void setTestAudioRoute(Boolean builtinSpeaker, Boolean bluetooth) {
        testIsBuiltinSpeaker = builtinSpeaker;
        testIsBluetoothOutput = bluetooth;
    }

    @androidx.annotation.VisibleForTesting
    public static void resetTestAudioRoute() {
        testIsBuiltinSpeaker = null;
        testIsBluetoothOutput = null;
    }

    @androidx.annotation.VisibleForTesting
    public boolean wasBluetoothRequested() {
        return bluetoothRequested;
    }

    @androidx.annotation.VisibleForTesting
    public int getRunnerStartCount() {
        return runner != null ? runner.getStartCount() : 0;
    }

    @androidx.annotation.VisibleForTesting
    public boolean isSafetyDialogShowing() {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        try {
            fm.executePendingTransactions();
        } catch (RuntimeException ignored) { }
        androidx.fragment.app.Fragment fragment = fm.findFragmentByTag(TAG_SAFETY_DIALOG);
        return fragment instanceof androidx.fragment.app.DialogFragment
                && ((androidx.fragment.app.DialogFragment) fragment).getDialog() != null
                && ((androidx.fragment.app.DialogFragment) fragment).getDialog().isShowing();
    }

    @androidx.annotation.VisibleForTesting
    public boolean isBluetoothRationaleShowing() {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        try {
            fm.executePendingTransactions();
        } catch (RuntimeException ignored) { }
        androidx.fragment.app.Fragment fragment = fm.findFragmentByTag(TAG_BT_RATIONALE_DIALOG);
        return fragment instanceof androidx.fragment.app.DialogFragment
                && ((androidx.fragment.app.DialogFragment) fragment).getDialog() != null
                && ((androidx.fragment.app.DialogFragment) fragment).getDialog().isShowing();
    }

    @androidx.annotation.VisibleForTesting
    public void triggerStartStop() {
        handleStartStop();
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
        if (viewModel != null) {
            viewModel.setStarting(false);
            viewModel.setRunning(false);
        }
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
        stopMic();
        super.onPause();
    }
    @Override protected void onDestroy() { if (runner != null) runner.close(); super.onDestroy(); }
}
