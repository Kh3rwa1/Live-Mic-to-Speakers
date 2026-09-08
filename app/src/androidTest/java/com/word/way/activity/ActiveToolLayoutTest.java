package com.word.way.activity;

import android.content.Context;
import android.text.Layout;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.word.way.R;
import demo.ads.AdsHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run unchanged throughout the existing API/font-scale matrix; no microphone access needed. */
@RunWith(AndroidJUnit4.class)
public class ActiveToolLayoutTest {
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    private static void textFits(TextView text) {
        Layout layout = text.getLayout();
        assertNotNull(layout);
        int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        int height = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        assertTrue("Text is vertically clipped", layout.getHeight() <= height + 1);
        for (int i = 0; i < layout.getLineCount(); i++) {
            assertEquals("Essential text was ellipsized", 0, layout.getEllipsisCount(i));
            assertTrue("Text extends beyond its row", layout.getLineWidth(i) <= width + 1);
        }
    }
    @Test public void longRouteDoesNotReplaceActionOrClipText() {
        try (ActivityScenario<LiveMicrophoneActivity> screen = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            screen.onActivity(activity -> activity.renderMeter(75,
                    "USB audio interface with a long device name / Bluetooth receiver for the meeting room"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                TextView action = activity.findViewById(R.id.tv_start_stop_new);
                assertEquals(activity.getString(R.string.quality_mic_off), action.getText().toString());
                assertEquals(activity.getString(R.string.quality_start_microphone),
                        activity.findViewById(R.id.iv_start_stop_new).getContentDescription());
                assertEquals(75, ((ProgressBar) activity.findViewById(R.id.studio_input_level)).getProgress());
                textFits(action);
                textFits(activity.findViewById(R.id.studio_output_route));
            });
            screen.recreate();
            screen.onActivity(activity -> assertEquals(0,
                    ((ProgressBar) activity.findViewById(R.id.studio_input_level)).getProgress()));
        }
    }
    @Test public void emptyRecordAndHoldScreensDoNotOfferBrokenPreview() {
        try (ActivityScenario<RecordAudioActivity> screen = ActivityScenario.launch(RecordAudioActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.iv_play).isEnabled());
                assertEquals(activity.getString(R.string.studio_ready),
                        ((TextView) activity.findViewById(R.id.studio_feedback)).getText().toString());
                textFits(activity.findViewById(R.id.tv_timer));
                textFits(activity.findViewById(R.id.tv_start_stop_new));
            });
        }
        try (ActivityScenario<HoldToSpeakActivity> screen = ActivityScenario.launch(HoldToSpeakActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.iv_play).isEnabled());
                textFits(activity.findViewById(R.id.tv_start_stop_new));
            });
        }
    }
}
