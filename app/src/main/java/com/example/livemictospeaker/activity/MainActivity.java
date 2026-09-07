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
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.player.activity.MusicListActivity;
import demo.ads.GoogleAds;

public class MainActivity extends AppCompatActivity {
    private Class<?> pendingScreen;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Class<?> screen = pendingScreen;
                pendingScreen = null;
                if (granted && screen != null) startActivity(new Intent(this, screen));
                else if (!granted) Toast.makeText(this, "This feature needs permission. You can allow it in app Settings.", Toast.LENGTH_LONG).show();
            });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));
        findViewById(R.id.iv_back).setContentDescription("Back");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.cv_live_microphone).setOnClickListener(v -> onLiveMicrophoneClick());
        findViewById(R.id.cv_hold_to_speak).setOnClickListener(v -> onHoldToSpeakClick());
        findViewById(R.id.cv_record_audio).setOnClickListener(v -> onRecordAudioClick());
        findViewById(R.id.cv_music_list).setOnClickListener(v -> onMusicListClick());
    }
    private void openWithPermission(String required, Class<?> screen) {
        if (ContextCompat.checkSelfPermission(this, required) == PackageManager.PERMISSION_GRANTED)
            startActivity(new Intent(this, screen));
        else { pendingScreen = screen; permission.launch(required); }
    }
    // Media routing is managed by Android. Local microphone use never requires Bluetooth access.
    public void onLiveMicrophoneClick() { openWithPermission(Manifest.permission.RECORD_AUDIO, LiveMicrophoneActivity.class); }
    public void onHoldToSpeakClick() { openWithPermission(Manifest.permission.RECORD_AUDIO, HoldToSpeakActivity.class); }
    public void onRecordAudioClick() { openWithPermission(Manifest.permission.RECORD_AUDIO, RecordAudioActivity.class); }
    public void onMusicListClick() {
        openWithPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE, MusicListActivity.class);
    }
}
