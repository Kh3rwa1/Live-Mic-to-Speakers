package com.word.way.viewmodel;

import android.net.Uri;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewModelStateTest {

    @Test
    public void liveMicrophoneViewModelRetainsGainAndMeterState() {
        LiveMicrophoneViewModel vm = new LiveMicrophoneViewModel();
        assertEquals(0.8f, vm.getLiveGain(), 0.001f);
        vm.setLiveGain(0.45f);
        assertEquals(0.45f, vm.getLiveGain(), 0.001f);
        // Clamp bounds:
        vm.setLiveGain(1.5f);
        assertEquals(1.0f, vm.getLiveGain(), 0.001f);
        vm.setLiveGain(-0.2f);
        assertEquals(0.0f, vm.getLiveGain(), 0.001f);

        vm.setLastPeak(85);
        vm.setLastRoute("Wired Headset");
        assertEquals(85, vm.getLastPeak());
        assertEquals("Wired Headset", vm.getLastRoute());
    }

    @Test
    public void holdToSpeakViewModelRetainsQueueAndFile() {
        HoldToSpeakViewModel vm = new HoldToSpeakViewModel();
        assertTrue(vm.isQueueEmpty());

        File dummy = new File("/fake/path/recording.m4a");
        vm.setLastSaved(dummy);
        assertEquals(dummy, vm.getLastSaved());

        // Enqueue only adds actual files, empty queue remains empty with nonexistent file
        assertNull(vm.pollNext());
        assertTrue(vm.isQueueEmpty());
    }

    @Test
    public void recordAudioViewModelRetainsTimerAndFile() {
        RecordAudioViewModel vm = new RecordAudioViewModel();
        assertEquals(0L, vm.getStartedAt());
        assertNull(vm.getLastSaved());

        File recording = new File("/fake/path/record.m4a");
        vm.setLastSaved(recording);
        vm.setStartedAt(123456789L);

        assertEquals(recording, vm.getLastSaved());
        assertEquals(123456789L, vm.getStartedAt());
    }

    @Test
    public void musicPlayerViewModelRetainsStateAndPlaylist() {
        MusicPlayerViewModel vm = new MusicPlayerViewModel();
        assertTrue(vm.isAutoplay());
        assertEquals(0, vm.getPosition());
        assertEquals(0, vm.getPlaylist().length);

        vm.setPosition(45000);
        vm.setAutoplay(false);
        vm.setTitle("Test Song");
        vm.setArtist("Test Artist");

        assertEquals(45000, vm.getPosition());
        assertFalse(vm.isAutoplay());
        assertEquals("Test Song", vm.getTitle());
        assertEquals("Test Artist", vm.getArtist());

        File[] files = new File[]{new File("/fake/1.mp3"), new File("/fake/2.mp3")};
        vm.setPlaylist(files);
        assertArrayEquals(files, vm.getPlaylist());
    }
}
