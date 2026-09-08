package com.word.way.player.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import com.word.way.R;
import com.word.way.Utils.EUGeneralClass;
import com.word.way.player.fragment.LocalAudioPickerFragment;

public final class MusicListActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_music_list_new);
        EUGeneralClass.BottomNavigationColor(this);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tv_tittle), true);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, LocalAudioPickerFragment.Companion.newInstance()).commit();
    }
}
