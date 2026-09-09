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

        View root = content.getChildAt(0);
        if (root != null && root.getBackground() != null) {
            content.setBackground(root.getBackground().getConstantState() != null
                    ? root.getBackground().getConstantState().newDrawable(activity.getResources())
                    : root.getBackground());
        }

        // Set status bar and navigation bar icons to dark for light pastel background
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, content);
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);

        if (content.getTag(com.word.way.R.id.quality_insets_installed) != null) return;
        SafeAreaInsets padding = new SafeAreaInsets(content);
        content.setTag(com.word.way.R.id.quality_insets_installed, padding);
        ViewCompat.setOnApplyWindowInsetsListener(content, padding::apply);
        ViewCompat.requestApplyInsets(content);
    }
}
