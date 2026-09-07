package com.example.livemictospeaker.player;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class AudioMimeTypesTest {
    @Test public void m4aUsesMp4ContainerMime() { assertEquals("audio/mp4", AudioMimeTypes.forName("Recording.m4a")); }
    @Test public void supportedLegacyExtensionsHaveExplicitTypes() {
        assertEquals("audio/mpeg", AudioMimeTypes.forName("clip.mp3"));
        assertEquals("audio/wav", AudioMimeTypes.forName("clip.wav"));
        assertEquals("audio/aac", AudioMimeTypes.forName("clip.aac"));
    }
    @Test public void matchingIgnoresCaseAndUserLocale() {
        Locale old = Locale.getDefault();
        try { Locale.setDefault(new Locale("tr", "TR")); assertEquals("audio/mp4", AudioMimeTypes.forName("RECORDING.M4A")); }
        finally { Locale.setDefault(old); }
    }
    @Test public void rejectsMissingAndUnknownExtensions() {
        assertNull(AudioMimeTypes.forName(null)); assertNull(AudioMimeTypes.forName(""));
        assertNull(AudioMimeTypes.forName("secret.xml")); assertNull(AudioMimeTypes.forName("audio.m4a.exe"));
    }
}
