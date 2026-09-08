package com.word.way.Utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.word.way.R;

/** Shared, quiet status feedback and accessible roles for the audio controls. */
public final class ToolUi {
    private ToolUi() { }
    public static void button(View view) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
    }
    public static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.45f);
    }
    public static void level(Activity activity, int percent) {
        int value = Math.max(0, Math.min(100, percent));
        ((ProgressBar) activity.findViewById(R.id.studio_input_level)).setProgress(value);
        ((TextView) activity.findViewById(R.id.studio_level_value)).setText(
                activity.getString(R.string.studio_level_value, value));
        // No live-region announcements for a meter that changes ten times per second.
    }
    public static void permissionDenied(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity).setTitle(R.string.studio_permission_title)
                .setMessage(R.string.studio_permission_help)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.studio_open_settings, (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.getPackageName(), null)));
                    } catch (ActivityNotFoundException error) {
                        Toast.makeText(activity, R.string.studio_settings_unavailable, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }
}
