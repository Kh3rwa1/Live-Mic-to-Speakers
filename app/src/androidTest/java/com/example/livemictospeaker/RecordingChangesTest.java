package com.example.livemictospeaker;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.livemictospeaker.audio.RecordingChanges;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecordingChangesTest {
    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return lifecycle; }
    }
    private static void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void savedChangesFollowOwnerLifecycleAndWorkerPublication() throws Exception {
        AtomicReference<Owner> reference = new AtomicReference<>();
        List<Long> received = new ArrayList<>();
        main(() -> {
            Owner owner = new Owner(); reference.set(owner);
            owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            RecordingChanges.revisions().observe(owner, received::add);
        });
        Owner owner = reference.get();
        try {
            Thread worker = new Thread(RecordingChanges::notifySaved, "recording-publication-test");
            worker.start(); worker.join(3000);
            assertFalse("Publication worker did not finish", worker.isAlive());
            main(() -> { });
            assertEquals("Created owner received a callback", 0, received.size());
            main(() -> owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START));
            assertEquals(1, received.size());
            long first = received.get(0);
            main(RecordingChanges::notifySaved);
            assertEquals(2, received.size());
            assertTrue(received.get(1) > first);
            main(() -> owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP));
            main(() -> { RecordingChanges.notifySaved(); RecordingChanges.notifySaved(); });
            assertEquals("Stopped owner received callbacks", 2, received.size());
            main(() -> owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START));
            assertEquals("Restart should receive only the latest invalidation", 3, received.size());
            assertTrue(received.get(2) > received.get(1));
        } finally {
            main(() -> {
                if (owner.lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                    owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
                owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
            });
        }
        int before = received.size();
        main(RecordingChanges::notifySaved);
        assertEquals("Destroyed owner was retained", before, received.size());
    }
}
