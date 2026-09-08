package com.word.way.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.Utils.ToolUi;
import com.word.way.player.activity.MusicListActivity;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

/** Direct, accessible landing screen. Permission grants never start microphone capture. */
public class MainActivity extends AppCompatActivity implements AdConsent.HomeScreen {
    private Class<?> pendingScreen;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Class<?> screen = pendingScreen;
                pendingScreen = null;
                if (granted && screen != null && !isFinishing() && !isDestroyed()) startActivity(new Intent(this, screen));
                else if (!granted) ToolUi.permissionDenied(this);
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
        findViewById(R.id.tv_settings).setOnClickListener(v -> startActivity(new Intent(this, Setting_Activity.class)));
        attach(findViewById(R.id.cv_live_microphone), this::onLiveMicrophoneClick, R.string.quality_live_title, R.string.studio_live_description);
        attach(findViewById(R.id.cv_hold_to_speak), this::onHoldToSpeakClick, R.string.quality_hold_title, R.string.studio_hold_description);
        attach(findViewById(R.id.cv_record_audio), this::onRecordAudioClick, R.string.quality_record_title, R.string.studio_record_description);
        attach(findViewById(R.id.cv_music_list), this::onMusicListClick, R.string.quality_music_title, R.string.studio_library_description);
    }
    private void attach(View view, Runnable action, int title, int description) {
        ToolUi.button(view);
        view.setContentDescription(getString(R.string.studio_tool_summary, getString(title), getString(description)));
        view.setOnClickListener(v -> action.run());
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
