package com.word.way.activity;

import static demo.ads.AppUtil.rateApp;
import static demo.ads.AppUtil.shareApp;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.word.way.R;
import com.word.way.databinding.ActivitySettingNewBinding;
import com.word.way.util.SystemBars;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingNewBinding binding;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivitySettingNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().addNativeView(this, binding.nativeLay);
        binding.ivBack.setOnClickListener(v -> finish());
        binding.rlShareApp.setOnClickListener(v -> shareApp(this));
        binding.rlRate.setOnClickListener(v -> rateApp(this));
        binding.toolPrivacyChoices.setOnClickListener(v -> AdConsent.showPrivacyOptions(this));
        binding.rlPrivacyPolicy.setOnClickListener(v -> openPolicy());
        if (binding.rlAbout != null) {
            binding.rlAbout.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.settings_about)
                    .setMessage(R.string.quality_home_subtitle)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }

        if (binding.tvTitle != null) {
            binding.tvTitle.post(() -> {
                float textWidth = binding.tvTitle.getPaint().measureText(binding.tvTitle.getText().toString());
                if (textWidth > 0) {
                    binding.tvTitle.getPaint().setShader(new android.graphics.LinearGradient(
                            0, 0, textWidth, 0,
                            new int[]{0xFF111827, 0xFF111827, 0xFF1A9BF0, 0xFF0B6FD6, 0xFF4A7AB5, 0xFF8FB8DD},
                            new float[]{0.0f, 0.40f, 0.50f, 0.68f, 0.86f, 1.0f},
                            android.graphics.Shader.TileMode.CLAMP));
                    binding.tvTitle.invalidate();
                }
            });
        }
        if (binding.scroller != null) {
            binding.scroller.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
            binding.scroller.setVerticalScrollBarEnabled(false);
        }
        setupInputProfileSelection();
    }

    private void setupInputProfileSelection() {
        if (binding.rgAudioProfile == null) return;
        com.word.way.util.MyPref prefs = new com.word.way.util.MyPref(this);
        int currentProfile = prefs.getInt(com.word.way.util.MyPref.KEY_INPUT_PROFILE, com.word.way.util.MyPref.PROFILE_LOW_LATENCY);
        switch (currentProfile) {
            case com.word.way.util.MyPref.PROFILE_BALANCED:
                binding.rbProfileBalanced.setChecked(true);
                break;
            case com.word.way.util.MyPref.PROFILE_NOISY_ROOM:
                binding.rbProfileNoisy.setChecked(true);
                break;
            case com.word.way.util.MyPref.PROFILE_LOW_LATENCY:
            default:
                binding.rbProfileLowLatency.setChecked(true);
                break;
        }

        binding.rgAudioProfile.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedProfile;
            if (checkedId == R.id.rb_profile_balanced) {
                selectedProfile = com.word.way.util.MyPref.PROFILE_BALANCED;
            } else if (checkedId == R.id.rb_profile_noisy) {
                selectedProfile = com.word.way.util.MyPref.PROFILE_NOISY_ROOM;
            } else {
                selectedProfile = com.word.way.util.MyPref.PROFILE_LOW_LATENCY;
            }
            prefs.setInt(com.word.way.util.MyPref.KEY_INPUT_PROFILE, selectedProfile);
            Toast.makeText(this, R.string.settings_profile_updated_note, Toast.LENGTH_SHORT).show();
        });
    }
    @Override protected void onPostResume() { super.onPostResume(); AdConsent.request(this); }
    private void openPolicy() {
        Uri uri = Uri.parse(getString(R.string.privacy_policys));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            Toast.makeText(this, R.string.tool_privacy_missing, Toast.LENGTH_LONG).show(); return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException error) { Toast.makeText(this, R.string.tool_no_browser, Toast.LENGTH_LONG).show(); }
    }
}
