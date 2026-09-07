package com.example.livemictospeaker.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import demo.ads.AdConsent;
import demo.ads.GoogleAds;

public class StartActivity extends AppCompatActivity implements AdConsent.HomeScreen {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_start_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));
        findViewById(R.id.cv_start).setContentDescription("Open microphone and audio tools");
        findViewById(R.id.cv_start).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.cv_settings).setContentDescription("Settings and privacy choices");
        findViewById(R.id.cv_settings).setOnClickListener(v -> startActivity(new Intent(this, Setting_Activity.class)));
    }
    @Override protected void onPostResume() { super.onPostResume(); AdConsent.request(this); }
}
