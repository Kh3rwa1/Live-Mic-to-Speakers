package com.word.way;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.word.way.activity.*;
import demo.ads.AdsHandler;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests that each core screen supports screen rotation to landscape and back to portrait
 * without crashing or violating layout state.
 */
@RunWith(AndroidJUnit4.class)
public class ScreenRotationTest {

    private boolean previousAds;

    @Before
    public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app);
        previousAds = AdsHandler.isEnabledByUser();
        AdsHandler.setAdsOn(false);
    }

    @org.junit.After
    public void restoreAds() {
        AdsHandler.setAdsOn(previousAds);
    }

    private static void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static <T extends Activity> void rotateLandscapeAndPortrait(ActivityScenario<T> scenario) {
        idle();
        scenario.onActivity(activity -> {
            assertFalse(activity.isFinishing());
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        idle();
        scenario.onActivity(activity -> {
            assertFalse(activity.isFinishing());
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });
        idle();
        scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
    }

    @Test
    public void mainActivityRotatesWithoutCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            rotateLandscapeAndPortrait(scenario);
        }
    }

    @Test
    public void liveMicrophoneActivityRotatesWithoutCrash() {
        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            rotateLandscapeAndPortrait(scenario);
        }
    }

    @Test
    public void holdToSpeakActivityRotatesWithoutCrash() {
        try (ActivityScenario<HoldToSpeakActivity> scenario = ActivityScenario.launch(HoldToSpeakActivity.class)) {
            rotateLandscapeAndPortrait(scenario);
        }
    }

    @Test
    public void recordAudioActivityRotatesWithoutCrash() {
        try (ActivityScenario<RecordAudioActivity> scenario = ActivityScenario.launch(RecordAudioActivity.class)) {
            rotateLandscapeAndPortrait(scenario);
        }
    }

    @Test
    public void settingsActivityRotatesWithoutCrash() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            rotateLandscapeAndPortrait(scenario);
        }
    }

    @Test
    public void musicActivityRotatesWithoutCrash() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        File track = File.createTempFile("rotation-fixture-", ".wav", app.getCacheDir());
        ByteBuffer wav = ByteBuffer.allocate(44 + 1600).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + 1600);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1);
        wav.putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(1600);
        try (FileOutputStream out = new FileOutputStream(track)) {
            out.write(wav.array());
        }
        Intent intent = new Intent(app, MusicActivity.class)
                .putExtra("SONG_URI", Uri.fromFile(track).toString())
                .putExtra("SONG_NAME", "Audio preview")
                .putExtra("AUTOPLAY", false);
        try (ActivityScenario<MusicActivity> scenario = ActivityScenario.launch(intent)) {
            rotateLandscapeAndPortrait(scenario);
        } finally {
            assertTrue(track.delete());
        }
    }

    @Test
    public void musicListActivityRotatesWithoutCrash() {
        try (ActivityScenario<com.word.way.player.activity.MusicListActivity> scenario = ActivityScenario.launch(com.word.way.player.activity.MusicListActivity.class)) {
            rotateLandscapeAndPortrait(scenario);
        }
    }
}
