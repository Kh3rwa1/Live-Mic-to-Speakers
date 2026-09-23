package com.word.way;

import demo.ads.AdsApplication;
import demo.ads.AdsHandler;

public class Adsapps extends AdsApplication {
    @Override public void onCreate() {
        super.onCreate();
        // Committed IDs are the real production IDs (user-confirmed). Production builds may
        // override them via generated production resources; these are the fallback.
        if (AdsHandler.getBannerId().isEmpty()) {
            AdsHandler.setBannerId(getString(R.string.bnr_admob));
        }
        if (AdsHandler.getNativeId().isEmpty()) {
            AdsHandler.setNativeId(getString(R.string.native_admob));
        }
        if (AdsHandler.getNativeAdvanceId().isEmpty()) {
            AdsHandler.setNativeAdvanceId(getString(R.string.native_advance_admob));
        }
        if (AdsHandler.getInterstitialId().isEmpty()) {
            AdsHandler.setInterstitialId(getString(R.string.int_admob));
        }
        if (AdsHandler.getOpenAds().isEmpty()) {
            AdsHandler.setOpenAds(getString(R.string.app_open_admob));
        }
        if (AdsHandler.getRewardedId().isEmpty()) {
            try {
                String rewarded = getString(R.string.video_admob);
                if (rewarded != null && !rewarded.trim().isEmpty()) AdsHandler.setRewardedId(rewarded);
            } catch (android.content.res.Resources.NotFoundException ignored) { }
        }
    }
}
