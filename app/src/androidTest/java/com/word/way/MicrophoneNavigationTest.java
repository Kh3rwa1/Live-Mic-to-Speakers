package com.word.way;

import android.Manifest;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.word.way.activity.MainActivity;
import com.word.way.activity.LiveMicrophoneActivity;
import demo.ads.AdsHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class MicrophoneNavigationTest {
    // Intentionally no BLUETOOTH_CONNECT grant: phone-speaker mode must still be accessible.
    @Rule public GrantPermissionRule microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    @Before public void disableAds() {
        Context context = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(context);
        AdsHandler.setAdsOn(false);
    }

    private static void reveal(ActivityScenario<MainActivity> screen, int id) {
        screen.onActivity(activity -> {
            View target = activity.findViewById(id);
            target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), true);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void microphoneOpensWithoutBluetoothPermission() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            reveal(screen, R.id.cv_live_microphone);
            onView(withId(R.id.cv_live_microphone)).perform(click());
            onView(withId(R.id.iv_start_stop_new)).check(matches(withContentDescription("Start microphone")));
            pressBack();
        }
    }

    @Test public void openingAndRecreatingNeverStartsMicrophone() {
        try (ActivityScenario<LiveMicrophoneActivity> screen = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            onView(withId(R.id.tv_start_stop_new)).check(matches(withText("Microphone off · Tap to start")));
            screen.recreate();
            onView(withId(R.id.iv_start_stop_new)).check(matches(withContentDescription("Start microphone")));
        }
    }
}
