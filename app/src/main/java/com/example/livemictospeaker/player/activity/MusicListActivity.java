package com.example.livemictospeaker.player.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livemictospeaker.R;
import com.example.livemictospeaker.Utils.EUGeneralClass;
import com.example.livemictospeaker.player.fragment.LocalAudioPickerFragment;

public final class MusicListActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_music_list_new);
        EUGeneralClass.BottomNavigationColor(this);
        findViewById(R.id.iv_back).setContentDescription("Back");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, LocalAudioPickerFragment.Companion.newInstance()).commit();
    }
}
