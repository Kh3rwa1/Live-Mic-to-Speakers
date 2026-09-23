package com.word.way.activity;

import com.word.way.util.MyPref;
import java.io.File;

public class MySavedAnnounceActivity extends RecordingHistoryActivity {
    @Override protected File recordingsDirectory() { return new File(MyPref.recordingsDirectory(this)); }
    @Override protected String legacyFolder() { return "Recording"; }
}
