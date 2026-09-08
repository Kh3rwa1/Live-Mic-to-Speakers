package com.word.way;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.word.way.Utils.MyPref;
import com.word.way.activity.MySavedAnnounceActivity;
import com.word.way.player.activity.MusicListActivity;
import demo.ads.AdsHandler;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native integration/layout checks, not physical audio or screen-reader certification. */
@RunWith(AndroidJUnit4.class)
public final class LibraryQualityTest {
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    private static <T extends Activity> void await(ActivityScenario<T> screen, Predicate<T> condition) {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            screen.onActivity(activity -> ready.set(condition.test(activity)));
            if (ready.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Library did not reach the expected state");
    }
    private static <T extends Activity> void measured(ActivityScenario<T> screen, Consumer<T> update) throws Exception {
        CountDownLatch drawn = new CountDownLatch(1);
        screen.onActivity(activity -> {
            View root = activity.getWindow().getDecorView();
            root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
                    root.getViewTreeObserver().removeOnGlobalLayoutListener(this); drawn.countDown();
                }
            });
            update.accept(activity); root.requestLayout();
        });
        assertTrue("Native layout did not complete", drawn.await(10, TimeUnit.SECONDS));
    }
    private static void namedAction(View action) {
        assertNotNull(action); assertTrue(action.isClickable()); assertTrue(action.isFocusable());
        CharSequence label = action.getContentDescription();
        if (label == null && action instanceof TextView) label = ((TextView) action).getText();
        assertNotNull(label); assertTrue(label.length() > 0);
        float minimum = 48 * action.getResources().getDisplayMetrics().density - 1;
        assertTrue("Action narrower than 48dp", action.getWidth() >= minimum);
        assertTrue("Action shorter than 48dp", action.getHeight() >= minimum);
    }
    private static File recordingFixture(Context app) throws Exception {
        File folder = new File(MyPref.creatsDirsforApp(app));
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File file = File.createTempFile("library-quality-long-recording-filename-", ".wav", folder);
        ByteBuffer wav = ByteBuffer.allocate(44 + 1600).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + 1600);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1);
        wav.putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(1600);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(wav.array()); }
        return file;
    }
    @Test public void historySearchRestoresAndSharingIsDiscoverable() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        File fixture = recordingFixture(app);
        String unmatched = "___quality_no_match_5d7a8099___";
        try (ActivityScenario<MySavedAnnounceActivity> screen = ActivityScenario.launch(MySavedAnnounceActivity.class)) {
            await(screen, activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSongList);
                return list.getVisibility() == View.VISIBLE && list.getAdapter().getItemCount() > 0;
            });
            screen.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search)).setQuery(unmatched, false));
            await(screen, activity -> activity.getString(R.string.library_no_matches).contentEquals(
                    ((TextView) activity.findViewById(R.id.noData)).getText()));
            screen.recreate();
            await(screen, activity -> unmatched.contentEquals(((SearchView) activity.findViewById(R.id.search)).getQuery())
                    && activity.findViewById(R.id.library_clear).getVisibility() == View.VISIBLE);
            screen.onActivity(activity -> activity.findViewById(R.id.library_clear).performClick());
            await(screen, activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSongList);
                return list.getVisibility() == View.VISIBLE && list.findViewHolderForAdapterPosition(0) != null;
            });
            screen.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.rvSongList);
                View row = list.findViewHolderForAdapterPosition(0).itemView;
                namedAction(row.findViewById(R.id.play)); namedAction(row.findViewById(R.id.share_recording));
                TextView title = row.findViewById(R.id.title);
                assertNotNull(title.getLayout());
                for (int line = 0; line < title.getLineCount(); line++) assertEquals(0, title.getLayout().getEllipsisCount(line));
                assertEquals("", ((SearchView) activity.findViewById(R.id.search)).getQuery().toString());
            });
            ToolQualityTest.capture("history-library");
        } finally { assertTrue("Remove only this test's recording", fixture.delete()); }
    }
    @Test public void librarySearchSurvivesRecreationAndPreviewNeverOverlaysList() throws Exception {
        try (ActivityScenario<MusicListActivity> screen = ActivityScenario.launch(MusicListActivity.class)) {
            await(screen, activity -> activity.findViewById(R.id.search) != null);
            screen.onActivity(activity -> ((SearchView) activity.findViewById(R.id.search)).setQuery("remember this search", false));
            screen.recreate();
            await(screen, activity -> activity.findViewById(R.id.search) != null
                    && "remember this search".contentEquals(((SearchView) activity.findViewById(R.id.search)).getQuery()));
            measured(screen, activity -> {
                activity.findViewById(R.id.mediaPlayerLayout).setVisibility(View.VISIBLE);
                ((TextView) activity.findViewById(R.id.title)).setText("A long audio filename for checking wrapping and readable preview controls");
            });
            screen.onActivity(LibraryQualityTest::assertSeparatePanes);
            ToolQualityTest.capture("library-preview-layout");
            measured(screen, activity -> activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL));
            screen.onActivity(LibraryQualityTest::assertSeparatePanes);
            ToolQualityTest.capture("library-rtl");
        }
    }
    private static void assertSeparatePanes(MusicListActivity activity) {
        View list = activity.findViewById(R.id.library_list_pane), preview = activity.findViewById(R.id.mediaPlayerLayout);
        assertTrue(list.getHeight() > 0); assertTrue(preview.getHeight() > 0);
        int[] listAt = new int[2], previewAt = new int[2];
        list.getLocationOnScreen(listAt); preview.getLocationOnScreen(previewAt);
        assertTrue("Preview overlaps library rows", listAt[1] + list.getHeight() <= previewAt[1]);
        namedAction(activity.findViewById(R.id.play_pause));
        assertFalse("An empty preview must not offer playback", activity.findViewById(R.id.play_pause).isEnabled());
    }
}
