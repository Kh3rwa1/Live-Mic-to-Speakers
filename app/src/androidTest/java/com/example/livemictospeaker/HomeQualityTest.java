package com.example.livemictospeaker;

import android.content.Context;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.livemictospeaker.activity.MainActivity;
import demo.ads.AdsHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class HomeQualityTest {
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    @Test public void homeControlsHaveLabelsAndLargeTouchTargets() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(activity -> {
                float density = activity.getResources().getDisplayMetrics().density;
                int[] ids = {R.id.iv_back, R.id.cv_live_microphone, R.id.cv_hold_to_speak,
                        R.id.cv_record_audio, R.id.cv_music_list};
                for (int id : ids) {
                    View view = activity.findViewById(id);
                    assertNotNull(view.getContentDescription());
                    assertTrue(view.getContentDescription().length() > 0);
                    assertTrue(view.isClickable()); assertTrue(view.isFocusable());
                    assertTrue("Touch target too short", view.getHeight() >= 48 * density - 1);
                    assertTrue("Touch target too narrow", view.getWidth() >= 48 * density - 1);
                }
                assertEquals("Disabled ads must not reserve blank space", View.GONE,
                        activity.findViewById(R.id.nativeLay).getVisibility());
            });
        }
    }
    @Test public void settingsAndPrivacyStayReachableFromDirectHome() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.iv_back)).perform(click());
            onView(withId(R.id.rl_privacy_policy)).check(matches(isDisplayed()));
            pressBack();
            onView(withText(R.string.quality_home_title)).check(matches(isDisplayed()));
        }
    }
}
