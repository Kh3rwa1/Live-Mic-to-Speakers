package com.word.way.activity;

import android.app.Activity;
import android.content.Context;
import android.text.Layout;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.word.way.R;
import demo.ads.AdsHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
    private static <T extends Activity> void renderAndWait(ActivityScenario<T> screen, Consumer<T> change)
            throws InterruptedException {
        CountDownLatch laidOut = new CountDownLatch(1);
        screen.onActivity(activity -> {
            View root = activity.findViewById(android.R.id.content);
            root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    laidOut.countDown();
                    return true;
                }
            });
            change.accept(activity);
            root.requestLayout(); root.invalidate();
        });
        assertTrue("Layout did not reach a measured frame", laidOut.await(5, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static void textFits(TextView text) {
        Layout layout = text.getLayout();
        assertNotNull("Text has not been laid out", layout);
        int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        int height = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        assertTrue("Text is vertically clipped: " + layout.getHeight() + " > " + height,
                layout.getHeight() <= height + 1);
        for (int i = 0; i < layout.getLineCount(); i++) {
            assertEquals("Essential text was ellipsized", 0, layout.getEllipsisCount(i));
            // getLineWidth includes trailing wrap whitespace, which can extend beyond the row.
            // getLineMax excludes soft-wrap whitespace while retaining leading margins.
            float visibleWidth = layout.getLineMax(i);
            assertTrue("Visible text extends beyond its row: line " + i + ", " + visibleWidth
                    + " > " + width + ", text=" + text.getText(), visibleWidth <= width + 1);
        }
    }
    private static void measure(TextView text, int width, int height) {
        text.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        text.layout(0, 0, width, height);
    }
    private static void rejects(TextView text, String reason) {
        boolean rejected = false;
        try { textFits(text); }
        catch (AssertionError error) {
            assertTrue("Unexpected assertion: " + error.getMessage(), error.getMessage().contains(reason));
            rejected = true;
        }
        assertTrue("Layout check accepted real " + reason, rejected);
    }
    @Test public void boundsCheckIgnoresWrapWhitespaceButRejectsRealClipping() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextView text = new TextView(ApplicationProvider.getApplicationContext());
            text.setTextSize(20); text.setPadding(0, 0, 0, 0);
            // A following word forces a soft wrap; terminal whitespace is a different layout case.
            text.setText("OK                         OK");
            measure(text, (int) Math.ceil(text.getPaint().measureText("OK")) + 1, 1024);
            textFits(text);
            text.setSingleLine(true); text.setEllipsize(null);
            text.setText("WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW");
            measure(text, 80, 1024);
            rejects(text, "Visible text extends");
            text.setEllipsize(TextUtils.TruncateAt.END);
            measure(text, 80, 1024);
            rejects(text, "ellipsized");
            text.setEllipsize(null); text.setSingleLine(false);
            text.setText("One\nTwo\nThree");
            measure(text, 300, 1);
            rejects(text, "vertically clipped");
        });
    }
    @Test public void longRouteDoesNotReplaceActionOrClipText() throws Exception {
        try (ActivityScenario<LiveMicrophoneActivity> screen = ActivityScenario.launch(LiveMicrophoneActivity.class)) {
            renderAndWait(screen, activity -> activity.renderMeter(75,
                    "USB audio interface with a long device name / Bluetooth receiver for the meeting room"));
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
    @Test public void emptyRecordAndHoldScreensDoNotOfferBrokenPreview() throws Exception {
        try (ActivityScenario<RecordAudioActivity> screen = ActivityScenario.launch(RecordAudioActivity.class)) {
            renderAndWait(screen, activity -> { });
            screen.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.iv_play).isEnabled());
                assertEquals(activity.getString(R.string.studio_ready),
                        ((TextView) activity.findViewById(R.id.studio_feedback)).getText().toString());
                textFits(activity.findViewById(R.id.tv_timer));
                textFits(activity.findViewById(R.id.tv_start_stop_new));
            });
        }
        try (ActivityScenario<HoldToSpeakActivity> screen = ActivityScenario.launch(HoldToSpeakActivity.class)) {
            renderAndWait(screen, activity -> { });
            screen.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.iv_play).isEnabled());
                textFits(activity.findViewById(R.id.tv_start_stop_new));
            });
        }
    }
}
