package com.example.livemictospeaker.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.github.piasy.rxandroidaudio.AudioRecorder;
import com.github.piasy.rxandroidaudio.PlayConfig;
import com.github.piasy.rxandroidaudio.RxAmplitude;
import com.github.piasy.rxandroidaudio.RxAudioPlayer;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.thekhaeng.pushdownanim.PushDownAnim;
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;

import demo.ads.GoogleAds;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.internal.functions.Functions;
import io.reactivex.schedulers.Schedulers;


public class HoldToSpeakActivity extends RxAppCompatActivity implements AudioRecorder.OnErrorListener {
    private static final String TAG = "HoldToSpeakActivity";
    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_RATE = 128000;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    ImageView iv_back;
    ImageView iv_history;
    ImageView iv_mic;
    private File mAudioFile;
    private Queue<File> mAudioFiles = new LinkedList();
    private AudioRecorder mAudioRecorder;
    private Disposable mRecordDisposable;
    public RxAudioPlayer mRxAudioPlayer;
    MyPref myPref;
    RelativeLayout rel_ad_layout;
    ImageView rl_play;
    ImageView rl_start_stop;
    String value;
    private boolean isScoReceiverRegistered = false;
    private final BroadcastReceiver scoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.setBluetoothScoOn(true);
                }
                if (mRxAudioPlayer != null) {
                    mRxAudioPlayer.stopPlay();
                    startPlay();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_hold_to_speak_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().admobBanner(this, findViewById(R.id.nativeLay));

        this.myPref = new MyPref(this);
        this.iv_back = (ImageView) findViewById(R.id.iv_back);
        this.iv_mic = (ImageView) findViewById(R.id.iv_mic);
        this.iv_history = (ImageView) findViewById(R.id.iv_history);
        this.rl_start_stop = (ImageView) findViewById(R.id.iv_start_stop_new);
        this.rl_play = (ImageView) findViewById(R.id.iv_play);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(true);
            setupCommunicationDevice(audioManager);
        }
        this.mAudioRecorder = AudioRecorder.getInstance();
        this.mRxAudioPlayer = RxAudioPlayer.getInstance();
        this.mAudioRecorder.setOnErrorListener(this);
        this.rl_start_stop.setOnTouchListener(new View.OnTouchListener() {
            public final boolean onTouch(View view, MotionEvent motionEvent) {
                return HoldToSpeakActivity.this.handleHoldTouch(view, motionEvent);
            }
        });
        initBluetoothSco(audioManager);
        PushDownAnim.setPushDownAnimTo(this.iv_back, this.iv_history, this.rl_play).setOnClickListener((View.OnClickListener) new View.OnClickListener() {
            public void onClick(View view) {
                if (view == HoldToSpeakActivity.this.iv_back) {
                    HoldToSpeakActivity.this.onBackPressed();
                } else if (view == HoldToSpeakActivity.this.iv_history) {
                    HoldToSpeakActivity.this.startActivity(new Intent(HoldToSpeakActivity.this, MySavedHoldtoSpeakActivity.class));
                } else if (view == HoldToSpeakActivity.this.rl_play) {
                    HoldToSpeakActivity.this.startPlay();
                }
            }
        });
    }

    private void setupCommunicationDevice(AudioManager audioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                AudioDeviceInfo current = audioManager.getCommunicationDevice();
                if (current != null && current.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    return;
                }
                for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        audioManager.setCommunicationDevice(device);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "setCommunicationDevice failed", e);
            }
        }
    }

    private void initBluetoothSco(AudioManager audioManager) {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasBtPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (!hasBtPermission) return;
        }
        try {
            if (audioManager.isBluetoothScoAvailableOffCall()) {
                registerReceiver(this.scoReceiver,
                        new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
                this.isScoReceiverRegistered = true;
                audioManager.startBluetoothSco();
            }
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth SCO error", e);
        }
    }

    public boolean handleHoldTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            pressToRecord();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            releaseToSend();
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (this.isScoReceiverRegistered) {
            try {
                unregisterReceiver(this.scoReceiver);
                this.isScoReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.clearCommunicationDevice();
                }
            } catch (Exception ignored) {}
        }
        RxAudioPlayer rxAudioPlayer = this.mRxAudioPlayer;
        if (rxAudioPlayer != null) {
            rxAudioPlayer.stopPlay();
        }
        if (mRecordDisposable != null && !mRecordDisposable.isDisposed()) {
            mRecordDisposable.dispose();
        }
        this.compositeDisposable.dispose();
    }

    private void pressToRecord() {
        this.rl_start_stop.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.click_press_to_speak));
        this.iv_mic.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pink_microphone));
        recordAfterPermissionGranted();
    }

    private void recordAfterPermissionGranted() {
        this.mRecordDisposable = Observable.fromCallable(new Callable<Boolean>() {
            @Override
            public final Boolean call() throws Exception {
                return HoldToSpeakActivity.this.prepareRecordingFile();
            }
        }).flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
            @Override
            public final ObservableSource<Boolean> apply(Boolean ready) throws Exception {
                return HoldToSpeakActivity.this.playReadyTone(ready);
            }
        }).doOnComplete(new Action() {
            @Override
            public final void run() throws Exception {
                HoldToSpeakActivity.this.onRecordPrepared();
            }
        }).doOnNext(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean ready) throws Exception {
                Log.d(HoldToSpeakActivity.TAG, "startRecord success");
            }
        }).flatMap(new Function<Boolean, ObservableSource<Integer>>() {
            @Override
            public final ObservableSource<Integer> apply(Boolean ready) throws Exception {
                return HoldToSpeakActivity.this.observeAmplitude(ready);
            }
        }).compose(bindToLifecycle()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<Integer>() {
            @Override
            public final void accept(Integer amplitude) throws Exception {
                HoldToSpeakActivity.this.onAmplitudeUpdate(amplitude);
            }
        }, new Consumer<Throwable>() {
            public void accept(Throwable th) throws Exception {
                Log.e(TAG, "Record error", th);
            }
        });
    }

    public Boolean prepareRecordingFile() throws Exception {
        File dir = new File(MyPref.creatsDirsforholdspeak(this));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "rec_" + System.currentTimeMillis() + "_hp.mp3");
        this.mAudioFile = file;
        Log.d(TAG, "to prepare record");
        return Boolean.valueOf(this.mAudioRecorder.prepareRecord(1, 2, 1, SAMPLE_RATE, BIT_RATE, this.mAudioFile));
    }

    public ObservableSource<Boolean> playReadyTone(Boolean ready) throws Exception {
        return this.mRxAudioPlayer.play(PlayConfig.res(getApplicationContext(), R.raw.audio_record_ready).build());
    }

    public void onRecordPrepared() throws Exception {
        this.mAudioRecorder.startRecord();
    }

    public ObservableSource<Integer> observeAmplitude(Boolean ready) throws Exception {
        return RxAmplitude.from(this.mAudioRecorder);
    }

    public void onAmplitudeUpdate(Integer amplitude) throws Exception {
        int progress = this.mAudioRecorder.progress();
        Log.d(TAG, "amplitude: " + amplitude + ", progress: " + progress);
    }

    public void refreshGallery(File file) {
        if (file == null) return;
        try {
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()},
                    null, (path, uri) -> Log.d(TAG, "Scanned: " + path + " -> " + uri));
        } catch (Exception e) {
            Log.e(TAG, "Media scan failed", e);
        }
    }

    private void releaseToSend() {
        this.rl_start_stop.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.click_to_speak));
        this.iv_mic.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.yellow_microphone));
        Disposable disposable = this.mRecordDisposable;
        if (disposable != null && !disposable.isDisposed()) {
            this.mRecordDisposable.dispose();
            this.mRecordDisposable = null;
        }
        this.compositeDisposable.add(Observable.fromCallable(new Callable<Boolean>() {
            @Override
            public final Boolean call() throws Exception {
                return HoldToSpeakActivity.this.finalizeRecording();
            }
        }).compose(bindToLifecycle()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean saved) throws Exception {
                if (saved != null && saved && mAudioFile != null) {
                    refreshGallery(mAudioFile);
                }
            }
        }, new Consumer<Throwable>() {
            public void accept(Throwable th) throws Exception {
                Log.e(TAG, "Stop record error", th);
            }
        }));
    }

    public Boolean finalizeRecording() throws Exception {
        int stopRecord = this.mAudioRecorder.stopRecord();
        Log.d(TAG, "stopRecord: " + stopRecord);
        if (stopRecord < 2) {
            return false;
        }
        this.mAudioFiles.offer(this.mAudioFile);
        return true;
    }

    public void startPlay() {
        if (!this.mAudioFiles.isEmpty()) {
            this.rl_play.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.click_play));
            this.compositeDisposable.add(this.mRxAudioPlayer.play(PlayConfig.file(this.mAudioFiles.poll()).streamType(AudioManager.STREAM_MUSIC).build()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(Functions.emptyConsumer(), new Consumer<Throwable>() {
                public void accept(Throwable th) throws Exception {
                    Log.e(TAG, "Play error", th);
                }
            }, new Action() {
                @Override
                public final void run() {
                    HoldToSpeakActivity.this.startPlay();
                }
            }));
        }
    }

    @Override
    public void onError(final int code) {
        runOnUiThread(new Runnable() {
            public final void run() {
                HoldToSpeakActivity.this.showRecorderError(code);
            }
        });
    }

    public void showRecorderError(int code) {
        Toast.makeText(this, "Recording error: " + code, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        RxAudioPlayer rxAudioPlayer = this.mRxAudioPlayer;
        if (rxAudioPlayer != null) {
            rxAudioPlayer.stopPlay();
        }
        this.compositeDisposable.dispose();
        finish();
        AppConstants.overridePendingTransitionExit(this);
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
            this.value = this.myPref.getPref(MyPref.HoldSpeakActivity, "");
        } catch (Exception e) {
            Log.e(TAG, "onResume error", e);
        }
    }
}
