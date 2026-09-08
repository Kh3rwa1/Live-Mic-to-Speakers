package com.word.way.Utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Shared edge-to-edge safe-area handling ensuring true full-screen layout behind transparent system bars. */
public final class EUGeneralClass {
    private EUGeneralClass() { }

    public static void BottomNavigationColor(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        // Official AndroidX Edge-to-Edge API (Android 15+ compatible, zero deprecated calls)
        if (activity instanceof ComponentActivity) {
            EdgeToEdge.enable((ComponentActivity) activity,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            );
        }

        // Layout into display cutout (notch) on Android 9+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        // Make window background transparent so no theme window background clips
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        content.setBackgroundColor(Color.TRANSPARENT);
        content.setPadding(0, 0, 0, 0);

        // Set status bar and navigation bar icons to dark for light pastel background
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);

        View root = content.getChildAt(0);
        if (root.getTag(com.word.way.R.id.quality_insets_installed) != null) return;
        root.setTag(com.word.way.R.id.quality_insets_installed, Boolean.TRUE);

        final int initLeft = root.getPaddingLeft();
        final int initTop = root.getPaddingTop();
        final int initRight = root.getPaddingRight();
        final int initBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                initLeft + safe.left,
                initTop + safe.top,
                initRight + safe.right,
                initBottom + safe.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
