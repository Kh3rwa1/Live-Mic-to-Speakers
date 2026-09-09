package demo.ads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class GetSmartAdmob {
    private final SmartListener listener;
    private final String[] ids;
    public GetSmartAdmob(Context context, String[] ids, SmartListener listener) {
        this.ids = ids; this.listener = listener;
        if (context != null) AdsHandler.getInstance(context);
    }
    public GetSmartAdmob execute() {
        if (ids != null) {
            if (ids.length > 0) AdsHandler.bannerId = ids[0];
            if (ids.length > 1) AdsHandler.nativeId = ids[1];
            if (ids.length > 2) AdsHandler.interstitialId = ids[2];
            if (ids.length > 3) AdsHandler.openAds = ids[3];
            if (ids.length > 4) AdsHandler.rewardedId = ids[4];
            if (ids.length > 5) AdsHandler.nativeAdvanceId = ids[5];
        }
        if (AdsApplication.appOpenManager == null && AdsApplication.getInstance() != null
                && AdsHandler.openAds != null && !AdsHandler.openAds.isEmpty() && !"0".equals(AdsHandler.openAds)) {
            AdsApplication.appOpenManager = new AppOpenManager(AdsApplication.getInstance());
        }
        new Handler(Looper.getMainLooper()).post(() -> { if (listener != null) listener.onFinish(true); });
        return this;
    }
}
