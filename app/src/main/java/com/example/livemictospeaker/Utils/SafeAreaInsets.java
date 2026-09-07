package com.example.livemictospeaker.Utils;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

/** Applies system/keyboard safe areas against immutable original padding. */
public final class SafeAreaInsets {
    private final int left, top, right, bottom;
    public SafeAreaInsets(View view) {
        left = view.getPaddingLeft(); top = view.getPaddingTop();
        right = view.getPaddingRight(); bottom = view.getPaddingBottom();
    }
    public WindowInsetsCompat apply(View view, WindowInsetsCompat insets) {
        Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
        view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
        return WindowInsetsCompat.CONSUMED;
    }
}
