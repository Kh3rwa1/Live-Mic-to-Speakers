package com.example.livemictospeaker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.activity.*;
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

/** Native layout checks; these do not certify TalkBack usability or real audio quality. */
@RunWith(AndroidJUnit4.class)
public class ToolQualityTest {
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    private static void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private static void capture(String name) throws Exception {
        idle();
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Native screenshot unavailable", screenshot);
        Context app = ApplicationProvider.getApplicationContext();
        File folder = app.getExternalFilesDir("quality-screenshots");
        assertNotNull(folder);
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { screenshot.recycle(); }
    }
    private static <T extends Activity> void controls(ActivityScenario<T> screen, int... ids) {
        idle();
        for (int id : ids) {
            screen.onActivity(activity -> {
                View view = activity.findViewById(id);
                assertNotNull(view);
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
            });
            idle();
            screen.onActivity(activity -> {
                View view = activity.findViewById(id);
                CharSequence label = view.getContentDescription();
                if (label == null && view instanceof TextView) label = ((TextView) view).getText();
                assertNotNull("Every control needs a name", label);
                assertTrue(label.length() > 0);
                assertTrue(view.isClickable()); assertTrue(view.isFocusable());
                float minimum = 48 * activity.getResources().getDisplayMetrics().density - 1;
                assertTrue("Control too narrow", view.getWidth() >= minimum);
                assertTrue("Control too short", view.getHeight() >= minimum);
                Rect visible = new Rect();
                assertTrue("Control cannot be reached by scrolling", view.getGlobalVisibleRect(visible));
                assertEquals(view.getWidth(), visible.width());
                assertEquals(view.getHeight(), visible.height());
            });
        }
    }
    @Test public void coreToolsHaveReachableNamedControls() throws Exception {
        try (ActivityScenario<RecordAudioActivity> screen = ActivityScenario.launch(RecordAudioActivity.class)) {
            controls(screen, R.id.iv_back, R.id.iv_history, R.id.iv_start_stop_new, R.id.iv_play);
            capture("record");
            screen.recreate(); controls(screen, R.id.iv_start_stop_new);
        }
        try (ActivityScenario<HoldToSpeakActivity> screen = ActivityScenario.launch(HoldToSpeakActivity.class)) {
            controls(screen, R.id.iv_back, R.id.iv_history, R.id.iv_start_stop_new, R.id.iv_play);
            capture("hold");
        }
        try (ActivityScenario<LiveMicrophoneActivity> screen = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            controls(screen, R.id.iv_back, R.id.iv_start_stop_new);
            capture("live");
        }
        try (ActivityScenario<Setting_Activity> screen = ActivityScenario.launch(Setting_Activity.class)) {
            controls(screen, R.id.iv_back, R.id.rl_privacy_policy, R.id.tool_privacy_choices, R.id.rl_share_app, R.id.rl_rate);
            capture("settings");
            screen.onActivity(activity -> activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL));
            controls(screen, R.id.iv_back, R.id.tool_privacy_choices);
            capture("settings-rtl");
        }
    }
    @Test public void playerControlsAreAccessibleWithoutAutoplay() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        File track = File.createTempFile("quality-fixture-", ".wav", app.getCacheDir());
        // A silent, valid mono PCM fixture, not microphone capture or a user recording.
        ByteBuffer wav = ByteBuffer.allocate(44 + 1600).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + 1600);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1);
        wav.putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(1600);
        try (FileOutputStream out = new FileOutputStream(track)) { out.write(wav.array()); }
        Intent intent = new Intent(app, MusicActivity.class).putExtra("SONG_URI", Uri.fromFile(track).toString())
                .putExtra("SONG_NAME", "Audio preview").putExtra("AUTOPLAY", false);
        try (ActivityScenario<MusicActivity> screen = ActivityScenario.launch(intent)) {
            controls(screen, R.id.iv_back, R.id.imageButtonPre, R.id.imageButtonPlayPause, R.id.imageButtonNext, R.id.fab);
            capture("player");
        } finally { assertTrue(track.delete()); }
    }
    @Test public void insetsAreIdempotentAndAccountForKeyboard() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            idle();
            screen.onActivity(activity -> {
                assertTrue(activity.getApplicationInfo().targetSdkVersion >= 36);
                ViewGroup content = activity.findViewById(android.R.id.content);
                WindowInsetsCompat original = ViewCompat.getRootWindowInsets(content);
                WindowInsetsCompat keyboard = new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(7, 17, 11, 19))
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 83))
                        .setVisible(WindowInsetsCompat.Type.ime(), true).build();
                try {
                    ViewCompat.dispatchApplyWindowInsets(content, keyboard);
                    assertEquals(7, content.getPaddingLeft()); assertEquals(17, content.getPaddingTop());
                    assertEquals(11, content.getPaddingRight()); assertEquals(83, content.getPaddingBottom());
                    EUGeneralClass.BottomNavigationColor(activity);
                    ViewCompat.dispatchApplyWindowInsets(content, keyboard);
                    assertEquals(17, content.getPaddingTop()); assertEquals(83, content.getPaddingBottom());
                } finally { if (original != null) ViewCompat.dispatchApplyWindowInsets(content, original); }
            });
        }
    }
}
