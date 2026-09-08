package com.word.way.player;

/** One shared, platform-independent state policy for history and device-library screens. */
public enum LibraryState {
    LOADING, ERROR, EMPTY, NO_MATCHES, CONTENT;

    public static LibraryState resolve(boolean loading, boolean failed, int total, int visible) {
        if (total < 0 || visible < 0 || visible > total) throw new IllegalArgumentException("Invalid library counts");
        if (loading) return LOADING;
        if (failed) return ERROR;
        if (total == 0) return EMPTY;
        return visible == 0 ? NO_MATCHES : CONTENT;
    }
}
