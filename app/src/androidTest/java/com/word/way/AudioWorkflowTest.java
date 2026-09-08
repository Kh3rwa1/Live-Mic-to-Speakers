package com.word.way;

import android.Manifest;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import com.word.way.Utils.MyPref;
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
    @Test public void realRecorderProducesReadableM4a() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        File folder = new File(MyPref.creatsDirsforApp(app));
        String[] existing = folder.list();
        Set<String> before = new HashSet<>(existing == null ? new ArrayList<>() : Arrays.asList(existing));
        File created = null;
        try (ActivityScenario<RecordAudioActivity> scenario = ActivityScenario.launch(RecordAudioActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.iv_start_stop_new).performClick());
            awaitLabel(scenario, "Stop");
            File[] premature = folder.listFiles(file -> file.getName().endsWith(".m4a") && !before.contains(file.getName()));
            assertNotNull(premature); assertEquals("Unfinished recording was published", 0, premature.length);
            SystemClock.sleep(1100);
            scenario.onActivity(activity -> activity.findViewById(R.id.iv_start_stop_new).performClick());
            awaitLabel(scenario, "Start");
            File[] newFiles = folder.listFiles(file -> file.getName().endsWith(".m4a") && !before.contains(file.getName()));
            assertNotNull(newFiles); assertEquals(1, newFiles.length); created = newFiles[0]; assertTrue(created.length() > 0);
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
                metadata.setDataSource(created.getAbsolutePath());
                assertTrue(Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) >= 500);
            } finally { metadata.release(); }
        } finally { if (created != null) assertTrue("Remove this test's own recording", created.delete()); }
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
            // Observe the callback on the UI thread after lifecycle delivery, not on the test thread.
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
