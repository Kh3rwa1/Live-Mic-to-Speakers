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
                48000,
                48000,
                96,
                384,
                768,
                2,
                "Wired Headphones",
                true,
                1.5f
        );

        AudioDiagnostics.update(sample);
        AudioDiagnostics current = AudioDiagnostics.get();

        assertEquals(48000, current.sampleRate);
        assertEquals(48000, current.nativeRate);
        assertEquals(96, current.framesPerBuffer);
        assertEquals(384, current.recordBufferBytes);
        assertEquals(768, current.trackBufferBytes);
        assertEquals(2, current.underrunCount);
        assertEquals("Wired Headphones", current.route);
        assertTrue(current.lowLatency);
        assertEquals(1.5f, current.userGain, 0.001f);
        assertTrue(current.timestamp > 0);

        String formatted = current.toFormattedString();
        assertTrue(formatted.contains("48000 Hz"));
        assertTrue(formatted.contains("Wired Headphones"));
        assertTrue(formatted.contains("Underruns: 2"));
        assertTrue(formatted.contains("Active (API 26+)"));
        assertTrue(formatted.contains("150%"));
    }

    @Test
    public void nullRoute_fallsBackSafely() {
        AudioDiagnostics sample = new AudioDiagnostics(
                44100,
                48000,
                128,
                512,
                1024,
                0,
                null,
                false,
                1.0f
        );
        assertEquals("Unknown", sample.route);
        assertTrue(sample.toFormattedString().contains("Legacy"));
    }

    @Test
    public void nullUpdate_isIgnoredSafely() {
        AudioDiagnostics original = AudioDiagnostics.get();
        AudioDiagnostics.update(null);
        assertEquals(original, AudioDiagnostics.get());
    }
}
