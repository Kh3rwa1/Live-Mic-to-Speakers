package com.word.way.player;

import java.util.Locale;

public final class AudioSearch {
    private AudioSearch() { }
    public static boolean matches(String title, CharSequence query) {
        String needle = query == null ? "" : query.toString().trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || (title != null && title.toLowerCase(Locale.ROOT).contains(needle));
    }
}
