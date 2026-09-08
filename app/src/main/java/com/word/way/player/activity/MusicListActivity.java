package com.word.way.player.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.player.fragment.LocalAudioPickerFragment;

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
