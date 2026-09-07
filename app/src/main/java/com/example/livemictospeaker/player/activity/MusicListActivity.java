package com.example.livemictospeaker.player.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.AppConstants;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.Utils.MyPref;
import com.example.livemictospeaker.player.fragment.LocalAudioPickerFragment;
import com.thekhaeng.pushdownanim.PushDownAnim;

public final class MusicListActivity extends AppCompatActivity {
    private ImageView iv_back;
    private FragmentManager mFragmentManager;
    MyPref myPref;
    String value;

    @Override 
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_music_list_new);
        EUGeneralClass.BottomNavigationColor(this);
        this.myPref = new MyPref(this);
       
        this.mFragmentManager = getSupportFragmentManager();
        this.iv_back = (ImageView) findViewById(R.id.iv_back);
        showAudioPicker();
        PushDownAnim.setPushDownAnimTo(this.iv_back).setOnClickListener(new View.OnClickListener() {
            public  void onClick(View view) {
                MusicListActivity.this.onBackPressed();
            }
        });
    }

    private  void showAudioPicker() {
        FragmentTransaction beginTransaction = this.mFragmentManager.beginTransaction();
        beginTransaction.replace(R.id.container, LocalAudioPickerFragment.Companion.newInstance());
        beginTransaction.commitAllowingStateLoss();
    }

    @Override 
    protected void onResume() {
        try {
            super.onResume();
            this.value = this.myPref.getPref(MyPref.MusicListActivity, "");

        } catch (Exception e) {
            e.toString();
        }
    }


    @Override 
    public void onBackPressed() {
        super.onBackPressed();
       
        finish();
        AppConstants.overridePendingTransitionExit(this);
    }
}
