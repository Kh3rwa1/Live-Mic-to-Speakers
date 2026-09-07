package com.example.livemictospeaker.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import demo.ads.GetSmartAdmob;


public class SplashActivity extends AppCompatActivity {
    MyPref myPref;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_splash);
        String[] adsUrls = new String[]{
                getString(R.string.bnr_admob)
                , getString(R.string.native_admob)
                , getString(R.string.int_admob)
                , getString(R.string.app_open_admob)
                , getString(R.string.video_admob)
        };
        new GetSmartAdmob(this, adsUrls, (success) -> {
        }).execute();
        EUGeneralClass.BottomNavigationColor(this);
        this.myPref = new MyPref(this);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                SplashActivity.this.HomeScreen();
            }
        }, 1500);
    }

    public void HomeScreen() {
        if (isFinishing() || isDestroyed()) return;
        startActivity(new Intent(this, StartActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
