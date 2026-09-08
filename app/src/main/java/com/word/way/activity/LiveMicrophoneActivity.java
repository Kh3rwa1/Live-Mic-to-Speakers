package com.word.way.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.Utils.ToolUi;
import com.word.way.audio.AndroidAudioSession;
import com.word.way.audio.AudioSessionRunner;
import com.word.way.audio.LiveAudioFailure;
import demo.ads.GoogleAds;

public class LiveMicrophoneActivity extends AppCompatActivity {
    private AudioSessionRunner runner;
    private View toggle;
    private TextView status, feedback;
    private boolean requested, visible;
    private AlertDialog startDialog;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                feedback.setText(granted ? R.string.quality_mic_permission_granted : R.string.quality_mic_permission_denied);
                if (!granted) ToolUi.permissionDenied(this);
            });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live_microphone_new);
        EUGeneralClass.BottomNavigationColor(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        toggle = findViewById(R.id.iv_start_stop_new);
        status = findViewById(R.id.tv_start_stop_new);
        feedback = findViewById(R.id.studio_feedback);
        ToolUi.button(toggle);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        runner = new AudioSessionRunner(ticket -> new AndroidAudioSession(getApplicationContext(),
                (peak, route) -> runOnUiThread(() -> {
                    if (visible && requested && runner.isCurrent(ticket)) renderMeter(peak, route);
                }), () -> runOnUiThread(() -> {
                    if (runner.isCurrent(ticket)) {
                        stopMic(); feedback.setText(R.string.quality_mic_interrupted);
                    }
                })), new AudioSessionRunner.Listener() {
            @Override public void onStarted(long generation) {
                runOnUiThread(() -> {
                    if (visible && runner.isCurrent(generation)) {
                        status.setText(R.string.quality_mic_on);
                        feedback.setText(R.string.tool_mic_active);
                    }
                });
            }
            @Override public void onError(long generation, Exception error) {
                runOnUiThread(() -> {
                    if (runner.isCurrent(generation)) showAudioError(error);
                });
            }
        });
        toggle.setOnClickListener(v -> {
            if (requested) { stopMic(); return; }
            if (startDialog != null && startDialog.isShowing()) return;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO); return;
            }
            startDialog = new AlertDialog.Builder(this).setTitle(R.string.quality_feedback_title)
                    .setMessage(R.string.quality_feedback_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.quality_start, (dialog, which) -> {
                        if (!visible || requested) return;
                        requested = true;
                        toggle.setContentDescription(getString(R.string.quality_stop_microphone));
                        ((ImageView) findViewById(R.id.studio_action_icon)).setImageResource(R.drawable.tool_stop);
                        status.setText(R.string.quality_mic_starting);
                        feedback.setText(R.string.quality_mic_starting);
                        runner.start();
                    }).create();
            startDialog.setOnDismissListener(dialog -> startDialog = null);
            startDialog.show();
        });
        stopMic();
    }
    // Kept package-visible for deterministic UI tests; no microphone is started by this method.
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
            case CANCELLED: message = R.string.live_error_cancelled; break;
            default: message = R.string.quality_mic_error;
        }
        feedback.setText(message);
    }
    // Package-visible so instrumentation can exercise long routes without starting capture.
    void renderMeter(int peak, String route) {
        ToolUi.level(this, peak);
        ((TextView) findViewById(R.id.studio_output_route)).setText(getString(R.string.studio_output_route, route));
    }
    private void stopMic() {
        requested = false;
        if (runner != null) runner.stop();
        toggle.setContentDescription(getString(R.string.quality_start_microphone));
        ((ImageView) findViewById(R.id.studio_action_icon)).setImageResource(R.drawable.tool_mic);
        status.setText(R.string.quality_mic_off);
        feedback.setText(R.string.studio_ready);
        ToolUi.level(this, 0);
        ((TextView) findViewById(R.id.studio_output_route)).setText(R.string.studio_output_idle);
    }
    @Override protected void onResume() { super.onResume(); visible = true; }
    @Override protected void onPause() {
        visible = false;
        if (startDialog != null) startDialog.dismiss();
        stopMic(); super.onPause();
    }
    @Override protected void onDestroy() { if (runner != null) runner.close(); super.onDestroy(); }
}
