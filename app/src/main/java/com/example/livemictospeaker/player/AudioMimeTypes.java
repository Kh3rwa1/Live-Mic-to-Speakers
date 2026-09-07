package com.example.livemictospeaker.player;

import java.util.Locale;

public final class AudioMimeTypes {
    private AudioMimeTypes() {}
    public static String forName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".aac")) return "audio/aac";
        return null;
    }
}
