package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import demo.ads.GoogleAds;

public class RecordAudioActivity extends AppCompatActivity {
    private static final String TAG = "RecordAudioActivity";
    private static final long MIN_CLICK_INTERVAL = 1000;

    private long startTime = 0;
    private long timeBuff = 0;
    private long millisecondTime = 0;
    private long updateTime = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRecordingRunning = false;
    private long lastClickTime = 0;

    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder;
    private MyPref myPref;

    private String outputDir;
    private String currentFilePath = null;

    private ImageView rl_start_stop;
    private ImageView iv_play;
    private TextView timer;
    private TextView tv_start_stop;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            millisecondTime = SystemClock.uptimeMillis() - startTime;
            updateTime = timeBuff + millisecondTime;

            int totalSeconds = (int) (updateTime / 1000);
            int seconds = totalSeconds % 60;
            int minutes = (totalSeconds / 60) % 60;
            long hours = totalSeconds / 3600;

            timer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
            handler.postDelayed(this, 100);
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean recordGranted = result.get(Manifest.permission.RECORD_AUDIO);
                if (recordGranted != null && recordGranted) {
                    startRecording();
                } else {
                    Toast.makeText(this, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_record_audio_new);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));
        EUGeneralClass.BottomNavigationColor(this);

        this.myPref = new MyPref(this);
        this.outputDir = MyPref.creatsDirsforApp(this);

        findViewById(R.id.iv_back).setOnClickListener(v -> onBackPressed());

        this.rl_start_stop = findViewById(R.id.iv_start_stop_new);
        this.iv_play = findViewById(R.id.iv_play);
        this.timer = findViewById(R.id.tv_timer);
        this.tv_start_stop = findViewById(R.id.tv_start_stop_new);

        findViewById(R.id.iv_history).setOnClickListener(v ->
                startActivity(new Intent(RecordAudioActivity.this, MySavedAnnounceActivity.class))
        );

        this.rl_start_stop.setOnClickListener(v -> {
            long elapsedRealtime = SystemClock.elapsedRealtime();
            if (elapsedRealtime - lastClickTime < MIN_CLICK_INTERVAL) {
                return;
            }
            lastClickTime = elapsedRealtime;

            if (!isRecordingRunning) {
                checkPermissionAndRecord();
            } else {
                stopRecording();
            }
        });

        this.iv_play.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                stopPlaying();
                return;
            }

            if (currentFilePath != null && new File(currentFilePath).exists()) {
                startPlaying(currentFilePath);
            } else {
                Toast.makeText(this, "No recorded audio found. Please record first.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
        }
    }

    private void startRecording() {
        stopPlaying();

        startTime = SystemClock.uptimeMillis();
        timeBuff = 0;
        updateTime = 0;
        handler.removeCallbacks(timerRunnable);
        handler.post(timerRunnable);

        if (!initMediaRecorder()) {
            handler.removeCallbacks(timerRunnable);
            Toast.makeText(this, "Failed to initialize recorder", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecordingRunning = true;
            rl_start_stop.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.click_time_start));
            tv_start_stop.setText("Stop");
        } catch (Exception e) {
            Log.e(TAG, "Error starting recorder", e);
            handler.removeCallbacks(timerRunnable);
            releaseRecorder();
            Toast.makeText(this, "Could not start audio recorder", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (!isRecordingRunning) return;

        handler.removeCallbacks(timerRunnable);
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recorder", e);
        } finally {
            releaseRecorder();
        }

        isRecordingRunning = false;
        rl_start_stop.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.time_start));
        tv_start_stop.setText("Start");

        if (currentFilePath != null) {
            scanFile(this, currentFilePath);
        }
    }

    private boolean initMediaRecorder() {
        try {
            releaseRecorder();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File outputFile = new File(outputDir, "Rec_" + timeStamp + ".m4a");
            currentFilePath = outputFile.getAbsolutePath();
            mediaRecorder.setOutputFile(currentFilePath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error preparing MediaRecorder", e);
            return false;
        }
    }

    private void startPlaying(String filePath) {
        stopPlaying();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            iv_play.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pause));

            mediaPlayer.setOnCompletionListener(mp -> stopPlaying());
        } catch (IOException e) {
            Log.e(TAG, "Error playing audio", e);
            stopPlaying();
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing player", e);
            }
            mediaPlayer = null;
        }
        iv_play.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.play));
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing recorder", e);
            }
            mediaRecorder = null;
        }
    }

    public static void scanFile(Context context, String path) {
        if (path == null) return;
        MediaScannerConnection.scanFile(context, new String[]{path}, null, (scanPath, uri) ->
                Log.i(TAG, "Scanned: " + scanPath + " -> " + uri)
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecordingRunning) {
            stopRecording();
        }
        stopPlaying();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
        releaseRecorder();
        stopPlaying();
    }

    @Override
    public void onBackPressed() {
        if (isRecordingRunning) {
            stopRecording();
        }
        stopPlaying();
        super.onBackPressed();
        AppConstants.overridePendingTransitionExit(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (myPref != null) {
                myPref.getPref(MyPref.RecordAudioActivity, "");
            }
        } catch (Exception ignored) {}
    }
}
