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
import com.word.way.Utils.EUGeneralClass;
import com.word.way.player.activity.MusicListActivity;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

/** Direct landing screen: one tap to a tool, with consent and settings still reachable. */
public class MainActivity extends AppCompatActivity implements AdConsent.HomeScreen {
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
        setContentView(R.layout.activity_main_new);
        EUGeneralClass.BottomNavigationColor(this);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.quality_headline), true);
        if (state != null) {
            for (Class<?> candidate : new Class<?>[]{LiveMicrophoneActivity.class, HoldToSpeakActivity.class,
                    RecordAudioActivity.class, MusicListActivity.class})
                if (candidate.getName().equals(state.getString("pendingScreen"))) pendingScreen = candidate;
        }
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));
        View settings = findViewById(R.id.tv_settings);
        if (settings != null) settings.setOnClickListener(v -> startActivity(new Intent(this, Setting_Activity.class)));
        View back = findViewById(R.id.iv_back);
        if (back != null) back.setOnClickListener(v -> startActivity(new Intent(this, Setting_Activity.class)));
        attachCardInteractions(findViewById(R.id.cv_live_microphone), this::onLiveMicrophoneClick);
        attachCardInteractions(findViewById(R.id.cv_hold_to_speak), this::onHoldToSpeakClick);
        attachCardInteractions(findViewById(R.id.cv_record_audio), this::onRecordAudioClick);
        attachCardInteractions(findViewById(R.id.cv_music_list), this::onMusicListClick);
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
