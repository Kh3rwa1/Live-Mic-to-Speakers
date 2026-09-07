package com.example.livemictospeaker.activity;

import com.example.livemictospeaker.Utils.MyPref;
import java.io.File;

public class MySavedAnnounceActivity extends RecordingHistoryActivity {
    @Override protected File recordingsDirectory() { return new File(MyPref.creatsDirsforApp(this)); }
    @Override protected String legacyFolder() { return "Recording"; }
}
