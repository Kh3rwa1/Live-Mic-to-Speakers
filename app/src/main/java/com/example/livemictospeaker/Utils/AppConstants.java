package com.example.livemictospeaker.Utils;

import android.app.Activity;
import com.example.livemictospeaker.R;


public class AppConstants {

    public static void overridePendingTransitionExit(Activity activity) {
        activity.overridePendingTransition(R.anim.activity_slide_from_left, R.anim.activity_slide_to_right);
    }
}
