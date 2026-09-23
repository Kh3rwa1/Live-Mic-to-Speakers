package com.word.way.player.activity;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import com.word.way.R;
import com.word.way.databinding.ActivityMusicListNewBinding;
import com.word.way.util.SystemBars;
import com.word.way.player.fragment.LocalAudioPickerFragment;
import demo.ads.GoogleAds;

public final class MusicListActivity extends AppCompatActivity {
    private ActivityMusicListNewBinding binding;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityMusicListNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().admobBanner(this, binding.nativeLay);
        ViewCompat.setAccessibilityHeading(binding.tvTitle, true);
        binding.ivBack.setOnClickListener(v -> finish());
        if (binding.tvTitle != null) {
            // Locale-safe full-text gradient; no hardcoded English word widths.
            binding.tvTitle.post(() -> {
                float textWidth = binding.tvTitle.getPaint().measureText(binding.tvTitle.getText().toString());
                if (textWidth > 0) {
                    binding.tvTitle.getPaint().setShader(new LinearGradient(
                            0, 0, textWidth, 0,
                            new int[]{0xFF111827, 0xFF1A9BF0, 0xFF0B6FD6, 0xFF8FB8DD},
                            new float[]{0.0f, 0.45f, 0.75f, 1.0f},
                            Shader.TileMode.CLAMP));
                    binding.tvTitle.invalidate();
                }
            });
        }
        if (binding.btnFilter != null) {
            binding.btnFilter.setOnClickListener(v -> {
                androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
                if (fragment instanceof LocalAudioPickerFragment) {
                    ((LocalAudioPickerFragment) fragment).showSortPopup(binding.btnFilter);
                }
            });
        }
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, LocalAudioPickerFragment.Companion.newInstance()).commit();
    }
}
