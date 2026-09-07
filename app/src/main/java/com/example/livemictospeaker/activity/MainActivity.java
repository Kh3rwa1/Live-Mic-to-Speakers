package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.player.activity.MusicListActivity;
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
        findViewById(R.id.iv_back).setOnClickListener(v -> startActivity(new Intent(this, Setting_Activity.class)));
        findViewById(R.id.cv_live_microphone).setOnClickListener(v -> onLiveMicrophoneClick());
        findViewById(R.id.cv_hold_to_speak).setOnClickListener(v -> onHoldToSpeakClick());
        findViewById(R.id.cv_record_audio).setOnClickListener(v -> onRecordAudioClick());
        findViewById(R.id.cv_music_list).setOnClickListener(v -> onMusicListClick());
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
