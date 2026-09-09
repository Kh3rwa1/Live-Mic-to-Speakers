package demo.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.common.util.IOUtils;
import java.io.IOException;
import java.io.InputStream;

public class AdsHandler {
    public static AdsHandler instance;
    public static String bannerId = "", nativeId = "", nativeAdvanceId = "", interstitialId = "", rewardedId = "", openAds = "";
    public static SharedPreferences sharedPreferences;
    public static SharedPreferences.Editor editor;
    public AdsHandler() { }
    public static byte[] getByte(Context context, int id) throws IOException {
        try (InputStream input = context.getResources().openRawResource(id)) { return IOUtils.toByteArray(input); }
    }
    public static void setAdsOn(boolean enabled) {
        if (editor != null) { editor.putBoolean("ads", enabled).apply(); }
        AdConsent.notifyAdsChanged();
    }
    public static boolean isEnabledByUser() { return sharedPreferences == null || sharedPreferences.getBoolean("ads", true); }
    public static boolean isAdsOn() { return isEnabledByUser() && AdConsent.canRequestAds(); }
    public static synchronized AdsHandler getInstance(Context context) {
        if (context != null && sharedPreferences == null) {
            sharedPreferences = context.getApplicationContext().getSharedPreferences("AdmobPref", Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();
        }
        if (instance == null) instance = new AdsHandler();
        return instance;
    }
    public static synchronized AdsHandler getInstance(Activity activity) { return getInstance((Context) activity); }
}
