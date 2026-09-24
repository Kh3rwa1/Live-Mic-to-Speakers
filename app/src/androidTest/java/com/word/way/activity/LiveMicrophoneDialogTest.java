package com.word.way.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.word.way.util.MyPref;
import demo.ads.AdsHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveMicrophoneDialogTest {

    @Rule
    public GrantPermissionRule microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    private boolean previousAds;
    private MyPref prefs;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app);
        previousAds = AdsHandler.isEnabledByUser();
        AdsHandler.setAdsOn(false);

        prefs = new MyPref(app);
        prefs.setBoolean(MyPref.SAFETY_ACK_SPEAKER, false);
        prefs.setBoolean(MyPref.BT_CONNECT_ASKED, false);
        LiveMicrophoneActivity.resetTestAudioRoute();
    }

    @After
    public void tearDown() {
        AdsHandler.setAdsOn(previousAds);
        LiveMicrophoneActivity.resetTestAudioRoute();
        if (prefs != null) {
            prefs.setBoolean(MyPref.SAFETY_ACK_SPEAKER, false);
            prefs.setBoolean(MyPref.BT_CONNECT_ASKED, false);
        }
    }

    private static void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void startWithSpeakerOutput_showsExactlyOneSafetyDialog_confirmStartsRunner() {
        LiveMicrophoneActivity.setTestAudioRoute(true, false);
        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                assertEquals(0, activity.getRunnerStartCount());
                assertFalse(activity.isSafetyDialogShowing());
                assertFalse(activity.isBluetoothRationaleShowing());

                activity.triggerStartStop();

                assertTrue(activity.isSafetyDialogShowing());
                assertFalse(activity.isBluetoothRationaleShowing());
                assertEquals(0, activity.getRunnerStartCount());

                activity.confirmStart();
                assertEquals(1, activity.getRunnerStartCount());
                assertFalse(activity.wasBluetoothRequested());
            });
        }
    }

    @Test
    public void startWithDontShowAgain_skipsDialogAndStartsDirectly() {
        LiveMicrophoneActivity.setTestAudioRoute(true, false);
        prefs.setBoolean(MyPref.SAFETY_ACK_SPEAKER, true);

        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                activity.triggerStartStop();
                assertFalse(activity.isSafetyDialogShowing());
                assertFalse(activity.isBluetoothRationaleShowing());
                assertEquals(1, activity.getRunnerStartCount());
            });
        }
    }

    @Test
    public void rapidDoubleTap_startsAudioSessionRunnerExactlyOnce() {
        LiveMicrophoneActivity.setTestAudioRoute(true, false);
        prefs.setBoolean(MyPref.SAFETY_ACK_SPEAKER, true);

        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                activity.triggerStartStop();
                activity.triggerStartStop();
                assertEquals(1, activity.getRunnerStartCount());
            });
        }
    }

    @Test
    public void rotationWithSafetyDialogOpen_keepsDialogVisibleAndConfirmStartsOnce() {
        LiveMicrophoneActivity.setTestAudioRoute(true, false);
        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                activity.triggerStartStop();
                assertTrue(activity.isSafetyDialogShowing());
            });

            // Rotate device to landscape
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            idle();

            // Rotated activity retains dialog
            scenario.onActivity(activity -> {
                assertTrue(activity.isSafetyDialogShowing());
                assertFalse(activity.isBluetoothRationaleShowing());
                assertEquals(0, activity.getRunnerStartCount());

                activity.confirmStart();
                assertEquals(1, activity.getRunnerStartCount());

                // Reset back to portrait
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            });
            idle();
        }
    }

    @Test
    public void bluetoothRoute_skipsSafetyDialog_andDoesNotPromptAgainOnceAsked() {
        LiveMicrophoneActivity.setTestAudioRoute(false, true);
        prefs.setBoolean(MyPref.BT_CONNECT_ASKED, false);

        try (ActivityScenario<LiveMicrophoneActivity> scenario = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                activity.triggerStartStop();
                // Bluetooth route: safety dialog should NOT be shown
                assertFalse(activity.isSafetyDialogShowing());
                assertEquals(1, activity.getRunnerStartCount());

                // Stop mic
                activity.triggerStartStop();
            });

            // Next start: ensure BT_CONNECT_ASKED avoids duplicate prompts
            prefs.setBoolean(MyPref.BT_CONNECT_ASKED, true);
            scenario.onActivity(activity -> {
                int countBefore = activity.getRunnerStartCount();
                activity.triggerStartStop();
                assertEquals(countBefore + 1, activity.getRunnerStartCount());
                assertFalse(activity.isSafetyDialogShowing());
            });
        }
    }
}
