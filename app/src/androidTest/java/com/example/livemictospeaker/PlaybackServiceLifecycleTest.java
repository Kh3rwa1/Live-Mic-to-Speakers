package com.example.livemictospeaker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import com.example.livemictospeaker.Service.MediaPlaybackService;
import com.example.livemictospeaker.audio.PlaybackState;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real service/decoder and lifecycle checks, not latency or physical-output certification. */
@RunWith(AndroidJUnit4.class)
public class PlaybackServiceLifecycleTest {
    @Rule public final ServiceTestRule serviceRule = new ServiceTestRule();
    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return lifecycle; }
    }
    private static void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private MediaPlaybackService bind() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        return ((MediaPlaybackService.IDBinder) serviceRule.bindService(new Intent(app, MediaPlaybackService.class))).getService();
    }
    @Test public void missingTrackReportsTypedErrorWithoutStaleObservers() throws Exception {
        MediaPlaybackService service = bind();
        AtomicReference<Owner> reference = new AtomicReference<>();
        AtomicInteger updates = new AtomicInteger();
        main(() -> {
            Owner owner = new Owner(); reference.set(owner);
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
            service.getPlaybackState().observe(owner, state -> updates.incrementAndGet());
            service.init(null, 0, false);
            PlaybackState error = service.getPlaybackState().getValue();
            assertNotNull(error);
            assertEquals(PlaybackState.Status.ERROR, error.getStatus());
            long sequence = error.getErrorSequence();
            assertTrue(sequence > 0);
            service.sendElapsedTime();
            assertEquals("Repeated snapshot must not create a new error", sequence,
                    service.getPlaybackState().getValue().getErrorSequence());
            assertFalse(service.wantsPlayback()); assertFalse(service.isPlaying());
        });
        main(() -> {
            Owner owner = reference.get();
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        });
        int before = updates.get();
        main(() -> { service.play(); service.stop(); });
        assertEquals("Destroyed screen still receives service state", before, updates.get());
    }
    @Test public void preparesWithoutAutoplayAndReleasesOnStop() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        File track = File.createTempFile("playback-state-", ".wav", app.getCacheDir());
        ByteBuffer wav = ByteBuffer.allocate(44 + 16000).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + 16000);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1);
        wav.putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(16000);
        try (FileOutputStream out = new FileOutputStream(track)) { out.write(wav.array()); }
        MediaPlaybackService service = bind();
        try {
            main(() -> service.init(Uri.fromFile(track), 0, false));
            AtomicReference<PlaybackState> observed = new AtomicReference<>();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            do {
                main(() -> observed.set(service.getPlaybackState().getValue()));
                assertNotNull(observed.get());
                if (observed.get().getStatus() == PlaybackState.Status.PAUSED) break;
                assertNotEquals("Decoder failed", PlaybackState.Status.ERROR, observed.get().getStatus());
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            assertEquals("Decoder did not prepare", PlaybackState.Status.PAUSED, observed.get().getStatus());
            assertTrue(observed.get().getDurationMs() > 0);
            main(() -> {
                assertFalse(service.wantsPlayback()); assertFalse(service.isPlaying());
                service.pause(); service.stop();
                assertNull(service.getFile()); assertEquals(0, service.getDuration());
                assertEquals(PlaybackState.Status.IDLE, service.getPlaybackState().getValue().getStatus());
            });
        } finally {
            main(service::stop);
            assertTrue(track.delete());
        }
    }
}
