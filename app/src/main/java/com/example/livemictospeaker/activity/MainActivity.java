package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Service.MediaPlaybackService;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.example.livemictospeaker.player.activity.MusicListActivity;
import com.thekhaeng.pushdownanim.PushDownAnim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import demo.ads.GoogleAds;

public class MainActivity extends AppCompatActivity {
    private MediaPlaybackService boundPlaybackService;

    private boolean isBinded = false;
    private MyPref myPref;

    private CardView cv_live_microphone;
    private CardView cv_hold_to_speak;
    private CardView cv_record_audio;
    private CardView cv_music_list;

    private Runnable pendingAction = null;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted && pendingAction != null) {
                    pendingAction.run();
                } else if (!allGranted) {
                    Toast.makeText(this, "Permission is required to use this feature", Toast.LENGTH_SHORT).show();
                }
                pendingAction = null;
            });

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            boundPlaybackService = ((MediaPlaybackService.IDBinder) iBinder).getService();
            isBinded = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            boundPlaybackService = null;
            isBinded = false;
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));

        this.myPref = new MyPref(this);

        findViewById(R.id.iv_back).setOnClickListener(v -> onBackPressed());

        this.cv_live_microphone = findViewById(R.id.cv_live_microphone);
        this.cv_hold_to_speak = findViewById(R.id.cv_hold_to_speak);
        this.cv_record_audio = findViewById(R.id.cv_record_audio);
        this.cv_music_list = findViewById(R.id.cv_music_list);

        PushDownAnim.setPushDownAnimTo(this.cv_live_microphone, this.cv_hold_to_speak, this.cv_record_audio, this.cv_music_list)
                .setOnClickListener(view -> {
                    if (view == cv_live_microphone) {
                        onLiveMicrophoneClick();
                    } else if (view == cv_hold_to_speak) {
                        onHoldToSpeakClick();
                    } else if (view == cv_record_audio) {
                        onRecordAudioClick();
                    } else if (view == cv_music_list) {
                        onMusicListClick();
                    }
                });
    }

    private void checkPermissionsAndRun(String[] permissions, Runnable action) {
        List<String> needed = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }
        if (needed.isEmpty()) {
            action.run();
        } else {
            this.pendingAction = action;
            this.permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    public void onLiveMicrophoneClick() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        checkPermissionsAndRun(perms.toArray(new String[0]), () ->
                startActivity(new Intent(this, LiveMicrophoneActivity.class)));
    }

    public void onHoldToSpeakClick() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        checkPermissionsAndRun(perms.toArray(new String[0]), () ->
                startActivity(new Intent(this, HoldToSpeakActivity.class)));
    }

    public void onRecordAudioClick() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.RECORD_AUDIO);
        checkPermissionsAndRun(perms.toArray(new String[0]), () ->
                startActivity(new Intent(this, RecordAudioActivity.class)));
    }

    public void onMusicListClick() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        checkPermissionsAndRun(perms.toArray(new String[0]), () ->
                startActivity(new Intent(this, MusicListActivity.class)));
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            Intent serviceIntent = new Intent(getApplicationContext(), MediaPlaybackService.class);
            getApplicationContext().bindService(serviceIntent, this.connection, BIND_AUTO_CREATE);
        } catch (Exception ignored) {}
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.isBinded) {
            try {
                getApplicationContext().unbindService(this.connection);
            } catch (Exception ignored) {}
            this.isBinded = false;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        AppConstants.overridePendingTransitionExit(this);
    }
}
