package com.word.way.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.word.way.R;
import demo.ads.GetSmartAdmob;

/** Plays the brand video once, then lands home. Missing/unplayable video falls back instantly. */
public class SplashActivity extends AppCompatActivity {
    private static final long MAX_SPLASH_MS = 8000;
    private boolean navigated;
    private android.widget.VideoView video;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeout = this::HomeScreen;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_splash);
        new GetSmartAdmob(this, new String[]{getString(R.string.bnr_admob), getString(R.string.native_admob),
                getString(R.string.int_admob), getString(R.string.app_open_admob), getString(R.string.video_admob),
                getString(R.string.native_advance_admob)},
                success -> { }).execute();
        video = findViewById(R.id.splash_video);
        int rawId = getResources().getIdentifier("splash_video", "raw", getPackageName());
        if (rawId == 0 || video == null) { showFallback(); HomeScreen(); return; }
        try {
            video.setVideoURI(android.net.Uri.parse("android.resource://" + getPackageName() + "/" + rawId));
        } catch (RuntimeException missing) { showFallback(); HomeScreen(); return; }
        video.setOnPreparedListener(player -> {
            player.setLooping(false);
            try { video.start(); } catch (RuntimeException error) { HomeScreen(); }
        });
        video.setOnCompletionListener(player -> HomeScreen());
        video.setOnErrorListener((player, what, extra) -> { HomeScreen(); return true; });
        handler.postDelayed(timeout, MAX_SPLASH_MS);
    }
    private void showFallback() {
        android.view.View fallback = findViewById(R.id.splash_fallback);
        if (fallback != null) fallback.setVisibility(android.view.View.VISIBLE);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(timeout);
        try { if (video != null && video.isPlaying()) video.pause(); } catch (RuntimeException ignored) { }
        super.onPause();
    }
    @Override protected void onPostResume() {
        super.onPostResume();
        if (navigated) return;
        if (video == null) { HomeScreen(); return; }
        // Re-arm the safety timeout after any backgrounding; completion/error navigate sooner.
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_SPLASH_MS);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(timeout);
        try { if (video != null) video.suspend(); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }
    public void HomeScreen() {
        if (navigated || isFinishing() || isDestroyed()) return;
        navigated = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
