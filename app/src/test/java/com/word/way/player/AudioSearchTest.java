package com.word.way.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioSearchTest {
    @Test public void usesTheQueryRatherThanMatchingTitleAgainstItself() {
        assertTrue(AudioSearch.matches("Evening Drum.wav", " drum "));
        assertFalse(AudioSearch.matches("Evening Drum.wav", "voice"));
    }
    @Test public void handlesNullAndEmptyValues() {
        assertTrue(AudioSearch.matches("Voice", null));
        assertTrue(AudioSearch.matches(null, " "));
        assertFalse(AudioSearch.matches(null, "voice"));
    }
    @Test public void matchingDoesNotDependOnDeviceLocale() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
            assertTrue(AudioSearch.matches("MIC RECORDING", "mic"));
        } finally { java.util.Locale.setDefault(before); }
    }
}
