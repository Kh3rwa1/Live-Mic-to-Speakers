package demo.ads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class GetSmartAdmob {
    private final Context context;
    private final SmartListener listener;
    private final String[] adsId;

    public GetSmartAdmob(Context context, String[] adsId, SmartListener listener) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.adsId = adsId;
        this.listener = listener;
        if (context != null) {
            AdsHandler.getInstance(context);
        }
    }

    public GetSmartAdmob execute() {
        boolean success = false;
        try {
            if (adsId != null) {
                if (adsId.length > 0) AdsHandler.bannerId = adsId[0];
                if (adsId.length > 1) AdsHandler.nativeId = adsId[1];
                if (adsId.length > 2) AdsHandler.interstitialId = adsId[2];
                if (adsId.length > 3) AdsHandler.openAds = adsId[3];
                if (adsId.length > 4) AdsHandler.rewardedId = adsId[4];
            }
            if (AdsHandler.openAds != null && !AdsHandler.openAds.isEmpty() && !AdsHandler.openAds.equals("0")) {
                AdsApplication.appOpenManager = new AppOpenManager(AdsApplication.getInstance());
            }
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) {
                listener.onFinish(finalSuccess);
            }
        });
        return this;
    }
}
