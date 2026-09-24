package com.word.way;

import android.content.Context;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.word.way.activity.MainActivity;
import demo.ads.AdsHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Verifies that the user's ads preference is preserved across Activity launches and is not
 * unconditionally overwritten on startup.
 */
@RunWith(AndroidJUnit4.class)
public class AdsPreferencePersistenceTest {

    private boolean originalAdsPreference;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(context);
        originalAdsPreference = AdsHandler.isEnabledByUser();
    }

    @After
    public void tearDown() {
        AdsHandler.setAdsOn(originalAdsPreference);
    }

    @Test
    public void adsOffPersistsAcrossRelaunch() {
        AdsHandler.setAdsOn(false);
        assertFalse("Precondition: ads disabled by user", AdsHandler.isEnabledByUser());

        // First launch
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse("Ads must remain disabled in first launch", AdsHandler.isEnabledByUser());
                View banner = activity.findViewById(R.id.nativeLay);
                if (banner != null) {
                    assertEquals("Ad placement view must not be visible when ads disabled",
                            View.GONE, banner.getVisibility());
                }
            });
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Second launch (relaunch)
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse("Ads must remain disabled after relaunch", AdsHandler.isEnabledByUser());
                View banner = activity.findViewById(R.id.nativeLay);
                if (banner != null) {
                    assertEquals("Ad placement view must not be visible when ads disabled on relaunch",
                            View.GONE, banner.getVisibility());
                }
            });
        }
    }

    @Test
    public void adsOnPersistsAcrossRelaunch() {
        AdsHandler.setAdsOn(true);
        assertTrue("Precondition: ads enabled by user", AdsHandler.isEnabledByUser());

        // First launch
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue("Ads must remain enabled in first launch", AdsHandler.isEnabledByUser());
            });
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Second launch (relaunch)
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue("Ads must remain enabled after relaunch", AdsHandler.isEnabledByUser());
            });
        }
    }
}
