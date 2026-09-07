package com.example.livemictospeaker.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.thekhaeng.pushdownanim.PushDownAnim;
import demo.ads.GoogleAds;

public class StartActivity extends AppCompatActivity {
    public int REQUEST_CODE = 107;
    CardView cv_settings;
    CardView cv_start;
    MyPref myPref;
    String value;

    @Override 
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_start_new);
        EUGeneralClass.BottomNavigationColor(this);
        GoogleAds.getInstance().addNativeView(this, findViewById(R.id.nativeLay));

        this.myPref = new MyPref(this);
       
        
        this.cv_start = (CardView) findViewById(R.id.cv_start);
        CardView cardView = (CardView) findViewById(R.id.cv_settings);
        this.cv_settings = cardView;
        PushDownAnim.setPushDownAnimTo(this.cv_start, cardView).setOnClickListener((View.OnClickListener) new View.OnClickListener() {
            

            public void onClick(View view) {
                if (view == StartActivity.this.cv_start) {
                    StartActivity.this.startActivity(new Intent(StartActivity.this, MainActivity.class));
                } else if (view == StartActivity.this.cv_settings) {
                    StartActivity.this.startActivity(new Intent(StartActivity.this, Setting_Activity.class));
                }
            }
        });
    }


    @SuppressLint("WrongConstant")
    @Override 
    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == this.REQUEST_CODE) {
            Toast.makeText(this, "Start Download", Toast.LENGTH_SHORT).show();
            if (i != -1) {
                Log.d("mmm", "Update flow failed" + i2);
            }
        }
    }

    @Override 
    protected void onResume() {
        try {
            super.onResume();
            this.value = this.myPref.getPref(MyPref.StartActivity, "");

        } catch (Exception e) {
            e.toString();
        }
    }
    @Override 
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override 
    protected void onPause() {
        super.onPause();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
