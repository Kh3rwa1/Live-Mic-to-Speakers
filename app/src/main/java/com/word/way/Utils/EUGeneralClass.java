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
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Shared safe-area handling for both the modern tools and legacy library screens. */
public final class EUGeneralClass {
    private EUGeneralClass() { }

    public static void BottomNavigationColor(Activity activity) {
        if (activity == null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View root = content.getChildAt(0);
        int canvas = root.getBackground() instanceof ColorDrawable
                ? ((ColorDrawable) root.getBackground()).getColor() : Color.WHITE;
        boolean light = ColorUtils.calculateLuminance(canvas) > 0.5;

        if (activity instanceof ComponentActivity) {
            EdgeToEdge.enable((ComponentActivity) activity);
        } else if (Build.VERSION.SDK_INT < 35) {
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Build.VERSION.SDK_INT < 26 ? Color.BLACK : Color.TRANSPARENT);
        }

        content.setBackgroundColor(canvas);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(activity.getWindow(), content);
        bars.setAppearanceLightStatusBars(light);
        bars.setAppearanceLightNavigationBars(light);

        if (content.getTag(com.word.way.R.id.quality_insets_installed) != null) return;
        SafeAreaInsets padding = new SafeAreaInsets(content);
        content.setTag(com.word.way.R.id.quality_insets_installed, padding);
        ViewCompat.setOnApplyWindowInsetsListener(content, padding::apply);
        ViewCompat.requestApplyInsets(content);
    }
}
