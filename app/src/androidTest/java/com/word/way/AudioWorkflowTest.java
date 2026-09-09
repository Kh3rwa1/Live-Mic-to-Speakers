package com.word.way;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import com.word.way.Utils.MyPref;
import com.word.way.activity.HoldToSpeakActivity;
import com.word.way.activity.MainActivity;
import com.word.way.activity.RecordAudioActivity;
import com.word.way.player.LocalAudio;
import com.word.way.player.adapter.AudioAdapter;
import demo.ads.AdsHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AudioWorkflowTest {
    @Rule public GrantPermissionRule microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext(); AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    private static void awaitLabel(ActivityScenario<RecordAudioActivity> scenario, String expected) {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        AtomicBoolean matched = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> matched.set(expected.contentEquals(((TextView) activity.findViewById(R.id.tv_start_stop_new)).getText())));
            if (matched.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Recorder did not reach UI state: " + expected);
    }
    private static void awaitHoldLabel(ActivityScenario<HoldToSpeakActivity> scenario, String expected) {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        AtomicBoolean matched = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> matched.set(expected.contentEquals(((TextView) activity.findViewById(R.id.tv_start_stop_new)).getText())));
            if (matched.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Hold recorder did not reach UI state: " + expected);
    }
    private static <T extends Activity> void awaitEnabled(ActivityScenario<T> scenario, int id) {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        AtomicBoolean enabled = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> enabled.set(activity.findViewById(id).isEnabled()));
            if (enabled.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Control did not become enabled: " + id);
    }
    private static Set<String> fileNames(File folder) {
        String[] existing = folder.list();
        return new HashSet<>(existing == null ? new ArrayList<>() : Arrays.asList(existing));
    }
    private static void deleteFilesCreatedByTest(File folder, Set<String> before) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!before.contains(file.getName())) assertTrue("Remove this test's own file", file.delete());
        }
    }
    private static void dispatchTouch(View view, int action, float x, float y, long downTime) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try { assertTrue("Hold control must consume its gesture", view.dispatchTouchEvent(event)); }
        finally { event.recycle(); }
    }
    @Test public void realRecorderProducesReadableM4aAndEnablesPreview() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        File folder = new File(MyPref.creatsDirsforApp(app));
        Set<String> before = fileNames(folder);
        try (ActivityScenario<RecordAudioActivity> scenario = ActivityScenario.launch(RecordAudioActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.iv_start_stop_new).performClick());
            awaitLabel(scenario, app.getString(R.string.tool_stop));
            File[] premature = folder.listFiles(file -> file.getName().endsWith(".m4a") && !before.contains(file.getName()));
            assertNotNull(premature); assertEquals("Unfinished recording was published", 0, premature.length);
            SystemClock.sleep(1100);
            scenario.onActivity(activity -> activity.findViewById(R.id.iv_start_stop_new).performClick());
            awaitLabel(scenario, app.getString(R.string.tool_start));
            awaitEnabled(scenario, R.id.iv_play);
            File[] newFiles = folder.listFiles(file -> file.getName().endsWith(".m4a") && !before.contains(file.getName()));
            assertNotNull(newFiles); assertEquals(1, newFiles.length); assertTrue(newFiles[0].length() > 0);
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
                metadata.setDataSource(newFiles[0].getAbsolutePath());
                assertTrue(Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) >= 500);
            } finally { metadata.release(); }
        } finally { deleteFilesCreatedByTest(folder, before); }
    }
    @Test public void holdRecordingEnablesPreviewAndSurvivesRecreation() {
        Context app = ApplicationProvider.getApplicationContext();
        File folder = new File(MyPref.creatsDirsforholdspeak(app));
        Set<String> before = fileNames(folder);
        try (ActivityScenario<HoldToSpeakActivity> scenario = ActivityScenario.launch(HoldToSpeakActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.iv_start_stop_new).performClick());
            awaitHoldLabel(scenario, app.getString(R.string.tool_hold_active));
            SystemClock.sleep(1100);
            scenario.onActivity(activity -> activity.findViewById(R.id.iv_start_stop_new).performClick());
            awaitHoldLabel(scenario, app.getString(R.string.tool_hold_idle));
            awaitEnabled(scenario, R.id.iv_play);
            File[] newFiles = folder.listFiles(file -> file.getName().endsWith(".m4a") && !before.contains(file.getName()));
            assertNotNull(newFiles); assertEquals(1, newFiles.length);
            scenario.recreate();
            awaitHoldLabel(scenario, app.getString(R.string.tool_hold_idle));
            awaitEnabled(scenario, R.id.iv_play);
        } finally { deleteFilesCreatedByTest(folder, before); }
    }
    @Test public void slidingOutsideHoldControlCancelsWithoutPublishing() {
        Context app = ApplicationProvider.getApplicationContext();
        File folder = new File(MyPref.creatsDirsforholdspeak(app));
        Set<String> before = fileNames(folder);
        AtomicLong downTime = new AtomicLong();
        try (ActivityScenario<HoldToSpeakActivity> scenario = ActivityScenario.launch(HoldToSpeakActivity.class)) {
            scenario.onActivity(activity -> {
                View control = activity.findViewById(R.id.iv_start_stop_new);
                long now = SystemClock.uptimeMillis(); downTime.set(now);
                dispatchTouch(control, MotionEvent.ACTION_DOWN, control.getWidth() / 2f, control.getHeight() / 2f, now);
            });
            awaitHoldLabel(scenario, app.getString(R.string.tool_hold_active));
            SystemClock.sleep(650);
            scenario.onActivity(activity -> {
                View control = activity.findViewById(R.id.iv_start_stop_new);
                dispatchTouch(control, MotionEvent.ACTION_MOVE, -1f, control.getHeight() / 2f, downTime.get());
                dispatchTouch(control, MotionEvent.ACTION_UP, -1f, control.getHeight() / 2f, downTime.get());
            });
            awaitHoldLabel(scenario, app.getString(R.string.tool_hold_idle));
            SystemClock.sleep(300);
            assertEquals("A cancelled hold must not publish a file", before, fileNames(folder));
        } finally { deleteFilesCreatedByTest(folder, before); }
    }
    @Test public void filteredAdapterKeepsPlaybackIdentityWithoutStaleIndexes() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<AudioAdapter> ref = new AtomicReference<>();
            CountDownLatch filtered = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                ArrayList<LocalAudio> audio = new ArrayList<>();
                audio.add(new LocalAudio(1, "Drum.wav", "", 1000, "content://media/external/audio/media/1", "0"));
                audio.add(new LocalAudio(2, "Voice.m4a", "", 1000, "content://media/external/audio/media/2", "0"));
                AudioAdapter adapter = new AudioAdapter(activity, audio, (item, position, view) -> { });
                adapter.setCurrentPlayingPos(1); ref.set(adapter); adapter.getFilter().filter("drum", count -> filtered.countDown());
            });
            assertTrue(filtered.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                assertEquals(1, ref.get().getItemCount()); assertEquals(2L, ref.get().getCurrentPlayingAudio().getAudioId());
                assertEquals(-1, ref.get().getCurrentPlayingPos()); ref.get().resetPreviouslyPlayedAudio();
            });
        }
    }
    private static void awaitAdCompletion(ActivityScenario<MainActivity> scenario, AtomicInteger calls) {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        AtomicBoolean delivered = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> {
                int count = calls.get();
                assertTrue("Ad completion must never be delivered twice", count <= 1);
                delivered.set(count == 1);
            });
            if (delivered.get()) return;
            SystemClock.sleep(20);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Ad completion was not delivered after the screen resumed");
    }
    @Test public void completedAdFlowWaitsForResumedScreen() {
        AtomicInteger calls = new AtomicInteger();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            scenario.onActivity(activity -> demo.ads.GoogleAds.getInstance().showCounterInterstitialAd(activity, calls::incrementAndGet));
            assertEquals(0, calls.get());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            awaitAdCompletion(scenario, calls);
            for (int i = 0; i < 3; i++) {
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
                scenario.onActivity(activity -> assertEquals(1, calls.get()));
            }
        }
    }
    @Test public void destroyedScreenDoesNotReceivePendingAdCompletion() {
        AtomicInteger calls = new AtomicInteger();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            scenario.onActivity(activity -> demo.ads.GoogleAds.getInstance().showCounterInterstitialAd(activity, calls::incrementAndGet));
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED);
            assertEquals(0, calls.get());
        }
    }
}
