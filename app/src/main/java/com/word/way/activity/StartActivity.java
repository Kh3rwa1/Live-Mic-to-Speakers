package com.word.way.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.word.way.R;
import com.word.way.databinding.ActivityStartNewBinding;
import com.word.way.util.SystemBars;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

public class StartActivity extends AppCompatActivity implements AdConsent.HomeScreen {
    private ActivityStartNewBinding binding;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityStartNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyEdgeToEdge(this);
        GoogleAds.getInstance().addNativeView(this, binding.nativeLay);
        binding.cvStart.setContentDescription(getString(R.string.quality_open_tools_desc));
        binding.cvStart.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        binding.cvSettings.setContentDescription(getString(R.string.quality_settings_desc));
        binding.cvSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
    @Override protected void onPostResume() { super.onPostResume(); AdConsent.request(this); }
}
