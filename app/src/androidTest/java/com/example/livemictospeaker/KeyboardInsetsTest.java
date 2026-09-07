package com.example.livemictospeaker;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.livemictospeaker.activity.MainActivity;
import demo.ads.AdsHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real IME coverage complements synthetic padding-policy tests on every supported API. */
@RunWith(AndroidJUnit4.class)
public class KeyboardInsetsTest {
    @Before public void disableAds() {
        Context app = ApplicationProvider.getApplicationContext();
        AdsHandler.getInstance(app); AdsHandler.setAdsOn(false);
    }
    @Test public void realKeyboardKeepsEditableControlVisible() throws Exception {
        AtomicReference<EditText> field = new AtomicReference<>();
        AtomicInteger previousMode = new AtomicInteger();
        AtomicBoolean ready = new AtomicBoolean();
        AtomicReference<String> diagnostic = new AtomicReference<>("Keyboard not yet visible");
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            try {
                screen.onActivity(activity -> {
                    previousMode.set(activity.getWindow().getAttributes().softInputMode);
                    activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    ViewGroup content = activity.findViewById(android.R.id.content);
                    EditText input = new EditText(activity);
                    input.setSingleLine(true); input.setText("Keyboard validation");
                    input.setContentDescription("Keyboard test field");
                    content.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
                    field.set(input); input.requestFocus();
                    WindowCompat.getInsetsController(activity.getWindow(), input).show(WindowInsetsCompat.Type.ime());
                });
                long deadline = SystemClock.elapsedRealtime() + 10_000;
                do {
                    screen.onActivity(activity -> {
                        ViewGroup content = activity.findViewById(android.R.id.content);
                        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
                        int keyboard = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                        Rect fieldBounds = new Rect(), visibleWindow = new Rect();
                        boolean visible = field.get().getGlobalVisibleRect(fieldBounds);
                        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleWindow);
                        boolean shown = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
                        ready.set(shown && keyboard > 0 && content.getPaddingBottom() >= keyboard && visible
                                && fieldBounds.height() == field.get().getHeight() && fieldBounds.bottom <= visibleWindow.bottom);
                        diagnostic.set("IME=" + keyboard + ", padding=" + content.getPaddingBottom()
                                + ", field=" + fieldBounds + ", visible window=" + visibleWindow);
                    });
                    if (ready.get()) break;
                    SystemClock.sleep(50);
                } while (SystemClock.elapsedRealtime() < deadline);
                assertTrue("Keyboard covered the field or was not observed: " + diagnostic.get(), ready.get());
                ToolQualityTest.capture("keyboard");
            } finally {
                screen.onActivity(activity -> {
                    EditText input = field.get();
                    if (input != null) {
                        WindowCompat.getInsetsController(activity.getWindow(), input).hide(WindowInsetsCompat.Type.ime());
                        ((ViewGroup) input.getParent()).removeView(input);
                    }
                    activity.getWindow().setSoftInputMode(previousMode.get());
                });
            }
        }
    }
}
