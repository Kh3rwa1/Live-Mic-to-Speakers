package demo.ads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class AdIdsInitializer {
    private final SmartListener listener;
    private final String[] ids;
    public AdIdsInitializer(Context context, String[] ids, SmartListener listener) {
        this.ids = ids; this.listener = listener;
        if (context != null) AdsHandler.getInstance(context);
    }
    public AdIdsInitializer execute() {
        if (ids != null) {
            if (ids.length > 0) AdsHandler.setBannerId(ids[0]);
            if (ids.length > 1) AdsHandler.setNativeId(ids[1]);
            if (ids.length > 2) AdsHandler.setInterstitialId(ids[2]);
            if (ids.length > 3) AdsHandler.setOpenAds(ids[3]);
            if (ids.length > 4) AdsHandler.setRewardedId(ids[4]);
            if (ids.length > 5) AdsHandler.setNativeAdvanceId(ids[5]);
        }
        if (AdsApplication.appOpenManager == null && AdsApplication.getInstance() != null
                && !AdsHandler.getOpenAds().isEmpty() && !"0".equals(AdsHandler.getOpenAds())) {
            AdsApplication.appOpenManager = new AppOpenManager(AdsApplication.getInstance());
        }
        new Handler(Looper.getMainLooper()).post(() -> { if (listener != null) listener.onFinish(true); });
        return this;
    }
}
