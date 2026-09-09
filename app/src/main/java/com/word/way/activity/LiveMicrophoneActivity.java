package com.word.way.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.audio.AndroidAudioSession;
import com.word.way.audio.AudioSessionRunner;
import demo.ads.GoogleAds;

public class LiveMicrophoneActivity extends AppCompatActivity {
    private AudioSessionRunner runner;
    private ImageView mic, toggle;
    private TextView status;
    private com.airbnb.lottie.LottieAnimationView lottiePulse, lottieSoundwave;
    private boolean requested, visible;
    private AlertDialog startDialog;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> Toast.makeText(this,
                    granted ? R.string.quality_mic_permission_granted : R.string.quality_mic_permission_denied,
                    Toast.LENGTH_LONG).show());
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_microphone_new);
        EUGeneralClass.BottomNavigationColor(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        mic = findViewById(R.id.iv_mic); toggle = findViewById(R.id.iv_start_stop_new);
        status = findViewById(R.id.tv_start_stop_new);
        lottiePulse = findViewById(R.id.lottie_mic_pulse);
        lottieSoundwave = findViewById(R.id.lottie_soundwave);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        View pill = findViewById(R.id.btn_live_pill);
        if (pill != null) pill.setOnClickListener(v -> toggle.performClick());
        View heroContainer = findViewById(R.id.layout_mic_hero);
        View.OnClickListener micTrigger = v -> toggle.performClick();
        mic.setOnClickListener(micTrigger);
        if (heroContainer != null) heroContainer.setOnClickListener(micTrigger);
        View.OnTouchListener micTouchFeedback = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mic.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100).start();
                    return true;
                case MotionEvent.ACTION_UP:
                    mic.animate().scaleX(requested ? 1.05f : 1.0f).scaleY(requested ? 1.05f : 1.0f).setDuration(160).start();
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    mic.animate().scaleX(requested ? 1.05f : 1.0f).scaleY(requested ? 1.05f : 1.0f).setDuration(160).start();
                    return true;
            }
            return false;
        };
        mic.setOnTouchListener(micTouchFeedback);
        if (heroContainer != null) heroContainer.setOnTouchListener(micTouchFeedback);
        runner = new AudioSessionRunner(ticket -> new AndroidAudioSession(getApplicationContext(),
                (peak, route) -> runOnUiThread(() -> {
                    if (visible && requested && runner.isCurrent(ticket))
                        status.setText(getString(R.string.quality_mic_meter, route, peak));
                }), () -> runOnUiThread(() -> {
                    if (runner.isCurrent(ticket)) {
                        stopMic(); Toast.makeText(this, R.string.quality_mic_interrupted, Toast.LENGTH_LONG).show();
                    }
                })), new AudioSessionRunner.Listener() {
            @Override public void onStarted(long generation) {
                runOnUiThread(() -> {
                    if (visible && runner.isCurrent(generation)) status.setText(R.string.quality_mic_on);
                });
            }
            @Override public void onError(long generation, Exception error) {
                runOnUiThread(() -> {
                    if (runner.isCurrent(generation)) {
                        stopMic(); Toast.makeText(LiveMicrophoneActivity.this, R.string.quality_mic_error, Toast.LENGTH_LONG).show();
                    }
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
                        mic.setContentDescription(getString(R.string.quality_stop_microphone));
                        toggle.setImageResource(R.drawable.tool_stop);
                        status.setText(R.string.quality_mic_starting);
                        if (lottiePulse != null) {
                            lottiePulse.setVisibility(View.VISIBLE);
                            lottiePulse.playAnimation();
                        }
                        if (lottieSoundwave != null) {
                            lottieSoundwave.setVisibility(View.VISIBLE);
                            lottieSoundwave.playAnimation();
                        }
                        mic.animate().scaleX(1.05f).scaleY(1.05f).setDuration(400).start();
                        runner.start();
                    }).create();
            startDialog.setOnDismissListener(dialog -> startDialog = null);
            startDialog.show();
        });
        stopMic();
    }
    private void stopMic() {
        requested = false;
        if (runner != null) runner.stop();
        toggle.setContentDescription(getString(R.string.quality_start_microphone));
        mic.setContentDescription(getString(R.string.quality_start_microphone));
        toggle.setImageResource(R.drawable.tool_mic);
        mic.setImageResource(R.drawable.hero_mic_3d);
        mic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
        if (lottiePulse != null) {
            lottiePulse.cancelAnimation();
            lottiePulse.setVisibility(View.INVISIBLE);
        }
        if (lottieSoundwave != null) {
            lottieSoundwave.cancelAnimation();
            lottieSoundwave.setVisibility(View.GONE);
        }
        status.setText(R.string.quality_mic_off);
    }
    @Override protected void onResume() { super.onResume(); visible = true; }
    @Override protected void onPause() {
        visible = false;
        if (startDialog != null) startDialog.dismiss();
        stopMic(); super.onPause();
    }
    @Override protected void onDestroy() { if (runner != null) runner.close(); super.onDestroy(); }
}
