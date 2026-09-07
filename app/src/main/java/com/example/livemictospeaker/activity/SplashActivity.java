package com.example.livemictospeaker.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.MyPref;
import demo.ads.GetSmartAdmob;

/** Initializes local ad identifiers only; consent is handled by the resumed home screen. */
public class SplashActivity extends AppCompatActivity {
    private boolean navigated;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_splash);
        new GetSmartAdmob(this, new String[]{getString(R.string.bnr_admob), getString(R.string.native_admob),
                getString(R.string.int_admob), getString(R.string.app_open_admob), getString(R.string.video_admob)},
                success -> { }).execute();
        new MyPref(this);
    }
    @Override protected void onPostResume() { super.onPostResume(); HomeScreen(); }
    public void HomeScreen() {
        if (navigated || isFinishing() || isDestroyed()) return;
        navigated = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
