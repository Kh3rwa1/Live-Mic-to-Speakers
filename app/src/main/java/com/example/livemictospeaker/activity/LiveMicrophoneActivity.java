package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.audio.AndroidAudioSession;
import com.example.livemictospeaker.audio.AudioSessionRunner;
import demo.ads.GoogleAds;

public class LiveMicrophoneActivity extends AppCompatActivity {
    private AudioSessionRunner runner;
    private ImageView mic, toggle;
    private TextView status;
    private boolean requested, visible;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) Toast.makeText(this, "Permission granted. Tap Start to use the microphone.", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "Allow microphone access in Settings to use this feature.", Toast.LENGTH_LONG).show();
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_microphone_new);
        EUGeneralClass.BottomNavigationColor(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        mic = findViewById(R.id.iv_mic);
        toggle = findViewById(R.id.iv_start_stop_new);
        status = findViewById(R.id.tv_start_stop_new);
        mic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View back = findViewById(R.id.iv_back);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        runner = new AudioSessionRunner(sessionTicket -> {
            return new AndroidAudioSession(getApplicationContext(), (peak, route) -> runOnUiThread(() -> {
                if (visible && requested && runner.isCurrent(sessionTicket))
                    status.setText("Output: " + route + "\nInput level: " + peak + "% · Tap to stop");
            }), () -> runOnUiThread(() -> {
                if (runner.isCurrent(sessionTicket)) {
                    stopMic();
                    Toast.makeText(this, "Microphone stopped: another app needs audio.", Toast.LENGTH_SHORT).show();
                }
            }));
        }, new AudioSessionRunner.Listener() {
            @Override public void onStarted(long generation) {
                runOnUiThread(() -> {
                    if (visible && runner.isCurrent(generation)) status.setText("Microphone on · Tap to stop");
                });
            }
            @Override public void onError(long generation, Exception error) {
                runOnUiThread(() -> {
                    if (runner.isCurrent(generation)) {
                        stopMic();
                        Toast.makeText(LiveMicrophoneActivity.this, "Audio stopped: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        toggle.setOnClickListener(v -> {
            if (requested) { stopMic(); return; }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
                return;
            }
            new AlertDialog.Builder(this).setTitle("Prevent loud feedback")
                    .setMessage("Start with low speaker volume. Keep the microphone away from speakers; headphones are safer. Echo cancellation cannot prevent all feedback.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Start", (dialog, which) -> {
                        if (!visible || requested) return;
                        requested = true;
                        toggle.setContentDescription("Stop microphone");
                        toggle.setImageResource(R.drawable.click_press_to_speak);
                        mic.setImageResource(R.drawable.pink_microphone);
                        status.setText("Starting microphone…");
                        runner.start();
                    }).show();
        });
        stopMic();
    }
    private void stopMic() {
        requested = false;
        if (runner != null) runner.stop();
        toggle.setContentDescription("Start microphone");
        toggle.setImageResource(R.drawable.click_to_speak);
        mic.setImageResource(R.drawable.yellow_microphone);
        status.setText("Microphone off · Tap to start");
    }
    @Override protected void onStart() { super.onStart(); visible = true; }
    @Override protected void onStop() { visible = false; stopMic(); super.onStop(); }
    @Override protected void onDestroy() { runner.close(); super.onDestroy(); }
}
