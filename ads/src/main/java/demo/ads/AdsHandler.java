package demo.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.common.util.IOUtils;
import java.io.IOException;
import java.io.InputStream;

/** Ad identifiers and consent-gated state. Values are only mutable through accessors. */
public class AdsHandler {
    private static AdsHandler instance;
    private static String bannerId = "", nativeId = "", nativeAdvanceId = "",
            interstitialId = "", rewardedId = "", openAds = "";
    private static SharedPreferences sharedPreferences;
    private static SharedPreferences.Editor editor;
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

    public static String getBannerId() { return bannerId; }
    public static void setBannerId(String id) { bannerId = id == null ? "" : id; }
    public static String getNativeId() { return nativeId; }
    public static void setNativeId(String id) { nativeId = id == null ? "" : id; }
    public static String getNativeAdvanceId() { return nativeAdvanceId; }
    public static void setNativeAdvanceId(String id) { nativeAdvanceId = id == null ? "" : id; }
    public static String getInterstitialId() { return interstitialId; }
    public static void setInterstitialId(String id) { interstitialId = id == null ? "" : id; }
    public static String getRewardedId() { return rewardedId; }
    public static void setRewardedId(String id) { rewardedId = id == null ? "" : id; }
    public static String getOpenAds() { return openAds; }
    public static void setOpenAds(String id) { openAds = id == null ? "" : id; }
}
