package com.word.way.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.word.way.R;
import com.word.way.databinding.ActivitySplashBinding;
import demo.ads.AdIdsInitializer;

/** Plays the brand video once, then lands home. Missing/unplayable video falls back instantly. */
public class SplashActivity extends AppCompatActivity {
    private static final long MAX_SPLASH_MS = 8000;
    private ActivitySplashBinding binding;
    private boolean navigated;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeout = this::navigateHome;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        new AdIdsInitializer(this, new String[]{getString(R.string.bnr_admob), getString(R.string.native_admob),
                getString(R.string.int_admob), getString(R.string.app_open_admob), getString(R.string.video_admob),
                getString(R.string.native_advance_admob)},
                success -> { }).execute();
        int rawId = getResources().getIdentifier("splash_video", "raw", getPackageName());
        if (rawId == 0 || binding.splashVideo == null) { showFallback(); navigateHome(); return; }
        try {
            binding.splashVideo.setVideoURI(android.net.Uri.parse("android.resource://" + getPackageName() + "/" + rawId));
        } catch (RuntimeException missing) { showFallback(); navigateHome(); return; }
        binding.splashVideo.setOnPreparedListener(player -> {
            player.setLooping(false);
            try { binding.splashVideo.start(); } catch (RuntimeException error) { navigateHome(); }
        });
        binding.splashVideo.setOnCompletionListener(player -> navigateHome());
        binding.splashVideo.setOnErrorListener((player, what, extra) -> { navigateHome(); return true; });
        handler.postDelayed(timeout, MAX_SPLASH_MS);
    }
    private void showFallback() {
        if (binding.splashFallback != null) binding.splashFallback.setVisibility(View.VISIBLE);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(timeout);
        try { if (binding.splashVideo != null && binding.splashVideo.isPlaying()) binding.splashVideo.pause(); } catch (RuntimeException ignored) { }
        super.onPause();
    }
    @Override protected void onPostResume() {
        super.onPostResume();
        if (navigated) return;
        if (binding.splashVideo == null) { navigateHome(); return; }
        // Re-arm the safety timeout after any backgrounding; completion/error navigate sooner.
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_SPLASH_MS);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(timeout);
        try { if (binding.splashVideo != null) binding.splashVideo.suspend(); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }
    public void navigateHome() {
        if (navigated || isFinishing() || isDestroyed()) return;
        navigated = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
    @Deprecated
    public void HomeScreen() {
        navigateHome();
    }
}
