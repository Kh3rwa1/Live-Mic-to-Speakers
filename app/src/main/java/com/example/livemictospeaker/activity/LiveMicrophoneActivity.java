package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.thekhaeng.pushdownanim.PushDownAnim;

import java.util.List;

import demo.ads.GoogleAds;

public class LiveMicrophoneActivity extends AppCompatActivity {
    private static final String TAG = "LiveMicrophoneActivity";

    public volatile boolean isOn = false;
    private volatile boolean isRecording = false;

    private ImageView iv_back;
    private ImageView iv_mic;
    private ImageView iv_start_stop;

    private AudioManager manager;
    private int minBuffer;
    private int sampleRate = 44100;
    private MyPref myPref;

    private final Object audioLock = new Object();
    private AudioRecord record;
    private AudioTrack player;
    private Thread audioThread;

    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl gainControl;

    private boolean isReceiverRegistered = false;
    private final BroadcastReceiver scoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                if (manager != null) {
                    manager.setBluetoothScoOn(true);
                }
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                if (manager != null) {
                    manager.setBluetoothScoOn(false);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_live_microphone_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));

        this.myPref = new MyPref(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        this.iv_back = findViewById(R.id.iv_back);
        this.iv_mic = findViewById(R.id.iv_mic);
        this.iv_start_stop = findViewById(R.id.iv_start_stop_new);

        this.manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (this.manager != null) {
            this.manager.setMode(AudioManager.MODE_NORMAL);
            setupCommunicationDevice();
        }

        sampleRate = getBestSampleRate();
        initAudio();

        this.iv_start_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isOn) {
                    stopMic();
                } else {
                    startMic();
                }
            }
        });

        initBluetoothSco();

        PushDownAnim.setPushDownAnimTo(this.iv_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void setupCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager != null) {
            try {
                List<AudioDeviceInfo> devices = manager.getAvailableCommunicationDevices();
                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        manager.setCommunicationDevice(device);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed setting communication device", e);
            }
        }
    }

    private void initBluetoothSco() {
        if (manager == null) return;

        boolean hasBluetoothConnect = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothConnect = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        if (hasBluetoothConnect) {
            try {
                if (manager.isBluetoothScoAvailableOffCall()) {
                    registerReceiver(scoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
                    isReceiverRegistered = true;
                    manager.startBluetoothSco();
                }
            } catch (Exception e) {
                Log.e(TAG, "Bluetooth SCO init failed", e);
            }
        }
    }

    private void startMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        isOn = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iv_mic.setImageDrawable(ContextCompat.getDrawable(LiveMicrophoneActivity.this, R.drawable.pink_microphone));
                iv_start_stop.setImageDrawable(ContextCompat.getDrawable(LiveMicrophoneActivity.this, R.drawable.click_press_to_speak));
            }
        });

        isRecording = true;
        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                streamAudio();
            }
        }, "LiveMicAudioThread");
        audioThread.start();
    }

    private void stopMic() {
        isOn = false;
        isRecording = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    iv_mic.setImageDrawable(ContextCompat.getDrawable(LiveMicrophoneActivity.this, R.drawable.yellow_microphone));
                    iv_start_stop.setImageDrawable(ContextCompat.getDrawable(LiveMicrophoneActivity.this, R.drawable.click_to_speak));
                } catch (Exception ignored) {}
            }
        });

        endAudio();
    }

    public void initAudio() {
        synchronized (audioLock) {
            try {
                int channelIn = AudioFormat.CHANNEL_IN_MONO;
                int channelOut = AudioFormat.CHANNEL_OUT_MONO;
                int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

                int minRecBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioEncoding);
                int minPlayBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, audioEncoding);
                this.minBuffer = Math.max(minRecBuf, minPlayBuf);

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    record = new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            sampleRate,
                            channelIn,
                            audioEncoding,
                            minBuffer * 2
                    );

                    int sessionId = record.getAudioSessionId();
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId);
                        if (echoCanceler != null) {
                            echoCanceler.setEnabled(true);
                        }
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId);
                        if (noiseSuppressor != null) {
                            noiseSuppressor.setEnabled(true);
                        }
                    }
                    if (AutomaticGainControl.isAvailable()) {
                        gainControl = AutomaticGainControl.create(sessionId);
                        if (gainControl != null) {
                            gainControl.setEnabled(true);
                        }
                    }
                }

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setEncoding(audioEncoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .build();

                player = new AudioTrack(
                        audioAttributes,
                        audioFormat,
                        minBuffer * 2,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                );

            } catch (Exception e) {
                Log.e(TAG, "Audio init error", e);
            }
        }
    }

    private void streamAudio() {
        synchronized (audioLock) {
            if (record == null || player == null || record.getState() != AudioRecord.STATE_INITIALIZED) {
                initAudio();
            }
            if (record == null || player == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopMic();
                    }
                });
                return;
            }

            try {
                record.startRecording();
                player.play();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start record/playback", e);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopMic();
                    }
                });
                return;
            }
        }

        short[] buffer = new short[minBuffer];
        while (isRecording) {
            int read = 0;
            synchronized (audioLock) {
                if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    read = record.read(buffer, 0, buffer.length);
                }
            }

            if (read > 0) {
                synchronized (audioLock) {
                    if (player != null && player.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        player.write(buffer, 0, read);
                    }
                }
            } else if (read < 0) {
                break;
            }
        }

        endAudio();
    }

    public void endAudio() {
        synchronized (audioLock) {
            isRecording = false;

            if (record != null) {
                try {
                    if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                    record.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping record", e);
                }
                record = null;
            }

            if (player != null) {
                try {
                    if (player.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        player.stop();
                    }
                    player.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping player", e);
                }
                player = null;
            }

            if (echoCanceler != null) {
                echoCanceler.release();
                echoCanceler = null;
            }
            if (noiseSuppressor != null) {
                noiseSuppressor.release();
                noiseSuppressor = null;
            }
            if (gainControl != null) {
                gainControl.release();
                gainControl = null;
            }
        }
    }

    public int getBestSampleRate() {
        int[] rates = {48000, 44100, 22050, 16000, 8000};
        for (int rate : rates) {
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                return rate;
            }
        }
        return 44100;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isOn) {
            stopMic();
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(scoReceiver);
                isReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
        if (manager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    manager.clearCommunicationDevice();
                }
            } catch (Exception ignored) {}
            try {
                manager.stopBluetoothSco();
                manager.setBluetoothScoOn(false);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isOn) {
            stopMic();
        }
    }

    @Override
    public void onBackPressed() {
        if (isOn) {
            stopMic();
        }
        super.onBackPressed();
        AppConstants.overridePendingTransitionExit(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (myPref != null) {
                myPref.getPref(MyPref.LiveMicrophoneActivity, "");
            }
        } catch (Exception ignored) {}
    }
}
