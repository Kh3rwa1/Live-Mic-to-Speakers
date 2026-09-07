package com.example.livemictospeaker;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.livemictospeaker.audio.AudioRouteGuard;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AudioRouteGuardTest {
    // No Bluetooth permission and no live microphone: this checks lifecycle registration only.
    // Physical wired/USB/Bluetooth removal still requires the device release checklist.
    @Test public void repeatedOpenCloseDoesNotDeliverStaleCallbacks() {
        Context app = ApplicationProvider.getApplicationContext();
        AtomicInteger callbacks = new AtomicInteger();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < 20; i++) {
                AudioRouteGuard guard = AudioRouteGuard.open(app, callbacks::incrementAndGet);
                guard.close(); guard.close();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(0, callbacks.get());
    }
}
