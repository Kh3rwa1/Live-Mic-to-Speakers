package com.example.livemictospeaker.Utils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.livemictospeaker.R;


public final class EUGeneralClass {
    private EUGeneralClass() {
    }

    public static void BottomNavigationColor(Activity activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.backgroundcolor));
            activity.getWindow().setNavigationBarColor(activity.getResources().getColor(R.color.backgroundcolor));
        }
    }



}
