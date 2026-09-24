package com.word.way.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void feedbackDetectorTelemetry_storedAndFormatted() {
        AudioDiagnostics sample = new AudioDiagnostics(
                true, true,
                "MIC", false, false,
                "LOW_LATENCY", 1920, 384, 0,
                96, 96, 48000, 48000,
                10.0f, 10.0f, 5000L, 0,
                "Speaker", 0.8f,
                42, -12.5f, -6.0f, 18, 0.45f,
                true, true, 0.003f, 0.95f, 1000.0f
        );
        assertEquals(42, sample.feedbackScore);
        assertEquals(-12.5f, sample.feedbackRmsDbfs, 0.01f);
        assertEquals(-6.0f, sample.feedbackPeakDbfs, 0.01f);
        assertEquals(18, sample.feedbackZcr);
        assertEquals(0.45f, sample.feedbackZcrVariance, 0.01f);
        assertTrue(sample.feedbackLoud);
        assertTrue(sample.feedbackTonal);
        assertEquals(0.003f, sample.feedbackFreqRelDev, 0.0001f);
        assertEquals(0.95f, sample.feedbackPurity, 0.01f);
        assertEquals(1000.0f, sample.feedbackFreqEst, 0.1f);

        String text = sample.toFormattedString();
        assertTrue(text.contains("Feedback Detector: score=42/200, loud=YES, tonal=YES"));
        assertTrue(text.contains("RMS=-12.5 dBFS"));
        assertTrue(text.contains("peak=-6.0 dBFS"));
        assertTrue(text.contains("ZCR=18 (var=0.45)"));
        assertTrue(text.contains("freq=1000.0 Hz"));
        assertTrue(text.contains("stability=0.30%"));
        assertTrue(text.contains("purity=95.0%"));
    }

    @Test
    public void detectorLogBuffer_toggleAndCsvExport() {
        DetectorLogBuffer logger = DetectorLogBuffer.getInstance();
        if (com.word.way.BuildConfig.DEBUG) {
            logger.setLoggingEnabled(false);
            assertEquals(0, logger.getLoggedCount());

            // Logging disabled: calls are ignored
            logger.logBlock(1000L, -10f, -5f, 20, 0.1f, true, true, 1000f, 0.002f, 0.98f, 10);
            assertEquals(0, logger.getLoggedCount());
            assertTrue(logger.exportCsv().isEmpty());

            // Enable logging
            logger.setLoggingEnabled(true);
            assertTrue(logger.isLoggingEnabled());
            logger.logBlock(1010L, -12f, -6f, 20, 0.1f, true, true, 1000f, 0.002f, 0.98f, 12);
            assertTrue(logger.getLoggedCount() >= 1);
            String csv = logger.exportCsv();
            assertTrue(csv.startsWith("timestamp_ms,rms_dbfs,peak_dbfs,zcr,zcr_var,loud,tonal,freq_hz,stability_pct,purity_pct,score"));
            assertTrue(csv.contains("1010,-12.0,-6.0,20,0.10,1,1,1000.0,0.20,98.0,12"));

            logger.setLoggingEnabled(false);
            assertEquals(0, logger.getLoggedCount());
        } else {
            // In release builds, verify the stub is permanently disabled and unreachable
            logger.setLoggingEnabled(true);
            assertFalse(logger.isLoggingEnabled());
            logger.logBlock(1010L, -12f, -6f, 20, 0.1f, true, true, 1000f, 0.002f, 0.98f, 12);
            assertEquals(0, logger.getLoggedCount());
            assertEquals("", logger.exportCsv());
        }
    }
}
