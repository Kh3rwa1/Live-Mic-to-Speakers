package com.word.way.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import android.view.View;
import android.view.MotionEvent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.word.way.R;
import com.word.way.databinding.ActivityMainNewBinding;
import com.word.way.util.SystemBars;
import com.word.way.player.activity.MusicListActivity;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

/** Direct landing screen: one tap to a tool, with consent and settings still reachable. */
public class MainActivity extends AppCompatActivity implements AdConsent.HomeScreen {
    private ActivityMainNewBinding binding;
    private Class<?> pendingScreen;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Class<?> screen = pendingScreen;
                pendingScreen = null;
                if (granted && screen != null && !isFinishing() && !isDestroyed()) startActivity(new Intent(this, screen));
                else if (!granted) Toast.makeText(this, R.string.quality_feature_permission, Toast.LENGTH_LONG).show();
            });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityMainNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyEdgeToEdge(this);
        ViewCompat.setAccessibilityHeading(binding.qualityHeadline, true);
        if (binding.qualityHeadline != null) {
            // Locale-safe: gradient across the full translated title, no hardcoded word split.
            binding.qualityHeadline.post(() -> {
                float textWidth = binding.qualityHeadline.getPaint().measureText(binding.qualityHeadline.getText().toString());
                if (textWidth > 0) {
                    binding.qualityHeadline.getPaint().setShader(new android.graphics.LinearGradient(
                            0, 0, textWidth, 0,
                            new int[]{0xFF111827, 0xFF1A9BF0, 0xFF0B6FD6},
                            new float[]{0.0f, 0.55f, 1.0f},
                            android.graphics.Shader.TileMode.CLAMP));
                    binding.qualityHeadline.invalidate();
                }
            });
        }
        if (state != null) {
            for (Class<?> candidate : new Class<?>[]{LiveMicrophoneActivity.class, HoldToSpeakActivity.class,
                    RecordAudioActivity.class, MusicListActivity.class})
                if (candidate.getName().equals(state.getString("pendingScreen"))) pendingScreen = candidate;
        }
        GoogleAds.getInstance().addNativeView(this, binding.nativeLay);
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        if (binding.cardVolumeSafety != null) {
            binding.cardVolumeSafety.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        attachCardInteractions(binding.cvLiveMicrophone, this::onLiveMicrophoneClick);
        attachCardInteractions(binding.cvHoldToSpeak, this::onHoldToSpeakClick);
        attachCardInteractions(binding.cvRecordAudio, this::onRecordAudioClick);
        attachCardInteractions(binding.cvMusicList, this::onMusicListClick);
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void attachCardInteractions(View view, Runnable onClick) {
        if (view == null) return;
        view.setOnClickListener(v -> onClick.run());
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    break;
            }
            return false;
        });
    }

    @Override protected void onPostResume() { super.onPostResume(); AdConsent.request(this); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (pendingScreen != null) state.putString("pendingScreen", pendingScreen.getName());
        super.onSaveInstanceState(state);
    }
    private void openWithPermission(String required, Class<?> screen) {
        if (pendingScreen != null) return;
        if (ContextCompat.checkSelfPermission(this, required) == PackageManager.PERMISSION_GRANTED)
            startActivity(new Intent(this, screen));
        else { pendingScreen = screen; permission.launch(required); }
    }
    public void onLiveMicrophoneClick() { openWithPermission(Manifest.permission.RECORD_AUDIO, LiveMicrophoneActivity.class); }
    public void onHoldToSpeakClick() { openWithPermission(Manifest.permission.RECORD_AUDIO, HoldToSpeakActivity.class); }
    public void onRecordAudioClick() { openWithPermission(Manifest.permission.RECORD_AUDIO, RecordAudioActivity.class); }
    public void onMusicListClick() {
        openWithPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE, MusicListActivity.class);
    }
}
