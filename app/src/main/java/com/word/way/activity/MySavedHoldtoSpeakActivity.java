package com.word.way.activity;

import com.word.way.util.MyPref;
import java.io.File;

public class MySavedHoldtoSpeakActivity extends RecordingHistoryActivity {
    @Override protected File recordingsDirectory() { return new File(MyPref.holdToSpeakDirectory(this)); }
    @Override protected String legacyFolder() { return "HPRecording"; }
}
