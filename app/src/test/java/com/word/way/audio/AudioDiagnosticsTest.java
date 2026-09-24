package com.word.way.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AudioDiagnosticsTest {

    @Test
    public void defaultDiagnostics_hasSafeValues() {
        AudioDiagnostics diag = AudioDiagnostics.get();
        assertNotNull(diag);
        assertNotNull(diag.route);
        assertNotNull(diag.toFormattedString());
    }

    @Test
    public void updateDiagnostics_storesAndFormatsCorrectly() {
        AudioDiagnostics sample = new AudioDiagnostics(
                true, false,
                "VOICE_PERFORMANCE (10)", true, false,
                "LOW_LATENCY", 1920, 384, 1,
                96, 96, 48000, 48000,
                16.5f, 16.0f, 15000L, 2,
                "Wired Headphones", 0.9f
        );

        AudioDiagnostics.update(sample);
        AudioDiagnostics current = AudioDiagnostics.get();

        assertTrue(current.featureLowLatency);
        assertEquals("VOICE_PERFORMANCE (10)", current.audioSource);
        assertTrue(current.aecEnabled);
        assertEquals("LOW_LATENCY", current.performanceMode);
        assertEquals(1920, current.bufferCapacityFrames);
        assertEquals(384, current.bufferSizeFrames);
        assertEquals(1, current.underrunCount);
        assertEquals(96, current.framesPerRead);
        assertEquals(96, current.burstFrames);
        assertEquals(48000, current.sampleRate);
        assertEquals(48000, current.nativeRate);
        assertEquals(16.5f, current.queueDepthMs, 0.01f);
        assertEquals(16.0f, current.queueDepth10sMs, 0.01f);
        assertEquals(15000L, current.sessionUptimeMs);
        assertEquals(2, current.driftCorrections);
        assertEquals("Wired Headphones", current.route);
        assertEquals(0.9f, current.userGain, 0.001f);
        assertTrue(current.timestamp > 0);

        String formatted = current.toFormattedString();
        assertTrue(formatted.contains("LowLatency=true"));
        assertTrue(formatted.contains("VOICE_PERFORMANCE (10)"));
        assertTrue(formatted.contains("capacity=1920 f"));
        assertTrue(formatted.contains("size=384 f"));
        assertTrue(formatted.contains("burst=96 f"));
        assertTrue(formatted.contains("read=96 f"));
        assertTrue(formatted.contains("16.5 ms"));
        assertTrue(formatted.contains("drift: +0.5 ms"));
        assertTrue(formatted.contains("Drift Corrections: 2"));
        assertTrue(formatted.contains("Wired Headphones"));
        assertTrue(formatted.contains("90%"));
    }

    @Test
    public void nullRoute_fallsBackSafely() {
        AudioDiagnostics sample = new AudioDiagnostics(
                false, false,
                null, false, false,
                null, 1024, 512, 0,
                128, 128, 44100, 48000,
                0f, -1f, 2000L, 0,
                null, 1.0f
        );
        assertEquals("Unknown", sample.route);
        assertEquals("Unknown", sample.audioSource);
        assertEquals("Unknown", sample.performanceMode);
        assertTrue(sample.toFormattedString().contains("measuring..."));
    }

    @Test
    public void nullUpdate_isIgnoredSafely() {
        AudioDiagnostics original = AudioDiagnostics.get();
        AudioDiagnostics.update(null);
        assertEquals(original, AudioDiagnostics.get());
    }
}
