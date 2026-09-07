package com.example.livemictospeaker.activity;

import static demo.ads.AppUtil.rateApp;
import static demo.ads.AppUtil.shareApp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;

import demo.ads.GoogleAds;


public class Setting_Activity extends AppCompatActivity {
    ImageView iv_back;
    MyPref myPref;
    RelativeLayout rl_privacy_policy;
    RelativeLayout rl_rate;
    RelativeLayout rl_share_app;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_setting_new);
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));

        EUGeneralClass.BottomNavigationColor(this);
        this.myPref = new MyPref(this);
        ImageView imageView = (ImageView) findViewById(R.id.iv_back);
        this.iv_back = imageView;
        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.rl_share_app);
        this.rl_share_app = relativeLayout;
        RelativeLayout relativeLayout2 = (RelativeLayout) findViewById(R.id.rl_rate);
        this.rl_rate = relativeLayout2;
        RelativeLayout relativeLayout3 = (RelativeLayout) findViewById(R.id.rl_privacy_policy);
        this.rl_privacy_policy = relativeLayout3;

        this.rl_share_app.setOnClickListener(new View.OnClickListener() {


            public void onClick(View view) {
                shareApp(Setting_Activity.this);
            }
        });
        this.rl_rate.setOnClickListener(new View.OnClickListener() {


            public void onClick(View view) {
                rateApp(Setting_Activity.this);
            }
        });
        this.rl_privacy_policy.setOnClickListener(new View.OnClickListener() {


            public void onClick(View view) {
                Setting_Activity.this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(getString(R.string.privacy_policys))));
            }
        });
        iv_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}





