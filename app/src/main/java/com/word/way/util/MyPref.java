package com.word.way.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.File;

/** App preferences. Ad identifiers and recording contents are never stored here. */
public class MyPref {
    private static final String PREF_NAME = "MIC_TO_SPEAK";
    /** Persisted live-monitoring gain, 0..1. */
    public static final String LiveMonitoringGain = "LiveMonitoringGain";
    /** True once Bluetooth connect permission was requested or handled. */
    public static final String BT_CONNECT_ASKED = "btConnectAsked";
    /** True once the user acknowledged speaker feedback safety warning. */
    public static final String SAFETY_ACK_SPEAKER = "safetyAckSpeaker";
    /** Input audio profile (0=Low Latency, 1=Balanced, 2=Noisy Room). Default is 0. */
    public static final String KEY_INPUT_PROFILE = "inputAudioProfile";
    public static final int PROFILE_LOW_LATENCY = 0;
    public static final int PROFILE_BALANCED = 1;
    public static final int PROFILE_NOISY_ROOM = 2;
    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public MyPref(Context context) {
        this.pref = context.getSharedPreferences(PREF_NAME, 0);
        this.editor = this.pref.edit();
    }

    public int getInt(String key, int fallback) {
        return this.pref.getInt(key, fallback);
    }

    public void setInt(String key, int value) {
        this.editor.putInt(key, value);
        this.editor.apply();
    }

    /** Hold-to-speak recordings folder (external app storage, internal as fallback). */
    public static String holdToSpeakDirectory(Context context) {
        return directory(context, "HPRecording");
    }

    /** General recordings folder (external app storage, internal as fallback). */
    public static String recordingsDirectory(Context context) {
        return directory(context, "Recording");
    }

    private static String directory(Context context, String name) {
        File baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        File file = new File(baseDir, name);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file.getAbsolutePath();
    }

    public void setPref(String str, float value) {
        this.editor.putFloat(str, value);
        this.editor.apply();
    }

    public float getPref(String str, float fallback) {
        return this.pref.getFloat(str, fallback);
    }

    public void setBoolean(String key, boolean value) {
        this.editor.putBoolean(key, value);
        this.editor.apply();
    }

    public boolean contains(String key) {
        return this.pref.contains(key);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return this.pref.getBoolean(key, fallback);
    }
}
