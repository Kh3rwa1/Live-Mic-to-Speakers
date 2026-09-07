package com.example.livemictospeaker.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import com.example.livemictospeaker.R;
import java.io.File;
import java.util.Set;


public class MyPref {
    public static final String HoldSpeakActivity = "HoldSpeakActivity";
    public static final String LiveMicrophoneActivity = "LiveMicrophoneActivity";
    public static final String MainActivity = "MainActivity";
    public static final String MusicActivity = "MusicActivity";
    public static final String MusicListActivity = "MusicListActivity";
    public static final String MySavedAnnounceActivity = "MySavedAnnounceActivity";
    public static final String MySavedHoldSpeakActivity = "MySavedHoldSpeakActivity";
    private static final String PREF_NAME = "MIC_TO_SPEAK";
    public static final String RecordAudioActivity = "RecordAudioActivity";
    public static final String RecordingListHoldSpeakActivity = "RecordingListHoldSpeakActivity";
    public static final String RecordingListRecordAudioActivity = "RecordingListRecordAudioActivity";
    public static final String StartActivity = "StartActivity";
    SharedPreferences.Editor editor;
    Context mContext;
    SharedPreferences pref;

    public MyPref(Context context) {
        this.mContext = context;
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, 0);
        this.pref = sharedPreferences;
        this.editor = sharedPreferences.edit();
    }

    public static String creatsDirsforholdspeak(Context context) {
        File baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        File file = new File(baseDir, "HPRecording");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file.getAbsolutePath();
    }

    public static String creatsDirsforApp(Context context) {
        File baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        File file = new File(baseDir, "Recording");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file.getAbsolutePath();
    }

    public void setPref(String str, Set<String> set) {
        this.editor.putStringSet(str, set);
        this.editor.apply();
    }

    public Set<String> getPref(String str, Set<String> set) {
        return this.pref.getStringSet(str, set);
    }

    public Integer getPref(String str, int i) {
        return Integer.valueOf(this.pref.getInt(str, i));
    }

    public String getPref(String str, String str2) {
        return this.pref.getString(str, str2);
    }

    public boolean getPref(String str, boolean z) {
        return this.pref.getBoolean(str, z);
    }

}
