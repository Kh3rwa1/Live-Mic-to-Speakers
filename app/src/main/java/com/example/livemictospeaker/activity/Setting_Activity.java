package com.example.livemictospeaker.activity;

import static demo.ads.AppUtil.rateApp;
import static demo.ads.AppUtil.shareApp;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

public class Setting_Activity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_setting_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));
        findViewById(R.id.iv_back).setContentDescription("Back");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.rl_share_app).setOnClickListener(v -> shareApp(this));
        findViewById(R.id.rl_rate).setOnClickListener(v -> rateApp(this));
        findViewById(R.id.rl_privacy_policy).setContentDescription("Privacy policy and ad privacy choices");
        findViewById(R.id.rl_privacy_policy).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Privacy")
                .setItems(new String[]{"View privacy policy", "Ad privacy choices"}, (dialog, which) -> {
                    if (which == 1) { AdConsent.showPrivacyOptions(this); return; }
                    Uri uri = Uri.parse(getString(R.string.privacy_policys));
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                        Toast.makeText(this, "Privacy policy is not configured. Please contact the developer.", Toast.LENGTH_LONG).show(); return;
                    }
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException error) { Toast.makeText(this, "No browser available to open the privacy policy.", Toast.LENGTH_LONG).show(); }
                }).show());
    }
}
