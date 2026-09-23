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
    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public MyPref(Context context) {
        this.pref = context.getSharedPreferences(PREF_NAME, 0);
        this.editor = this.pref.edit();
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
}
