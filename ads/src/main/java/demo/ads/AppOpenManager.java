package demo.ads;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

public class AppOpenManager implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private final AdsApplication application;
    private Activity activity;
    private AppOpenAd ad;
    private boolean loading, showing;
    private long loadedAt, lastShown;
    private boolean hasShown;
    public AppOpenManager(AdsApplication application) {
        this.application = application;
        application.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }
    public boolean isAdAvailable() { return ad != null && SystemClock.elapsedRealtime() - loadedAt < 4 * 60 * 60 * 1000L; }
    public void fetchAd() {
        if (loading || isAdAvailable() || !AdsHandler.isAdsOn() || AdsHandler.openAds == null
                || AdsHandler.openAds.isEmpty() || "0".equals(AdsHandler.openAds)) return;
        loading = true;
        // Mobile Ads SDK 23 removed the orientation argument and constants.
        AppOpenAd.load(application, AdsHandler.openAds, new AdRequest.Builder().build(), new AppOpenAd.AppOpenAdLoadCallback() {
            @Override public void onAdLoaded(AppOpenAd loaded) { ad = loaded; loadedAt = SystemClock.elapsedRealtime(); loading = false; }
            @Override public void onAdFailedToLoad(LoadAdError error) { loading = false; ad = null; }
        });
    }
    public void showAdIfAvailable() {
        if (!AdsHandler.isAdsOn() || showing) return;
        if (hasShown && SystemClock.elapsedRealtime() - lastShown < 60_000) return;
        if (!isAdAvailable()) { fetchAd(); return; }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { showing = false; ad = null; fetchAd(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { showing = false; ad = null; }
        });
        showing = true;
        hasShown = true;
        lastShown = SystemClock.elapsedRealtime();
        ad.show(activity);
    }
    @Override public void onStart(LifecycleOwner owner) { showAdIfAvailable(); }
    @Override public void onActivityStarted(Activity value) { if (!showing) activity = value; }
    @Override public void onActivityResumed(Activity value) { if (!showing) activity = value; }
    @Override public void onActivityDestroyed(Activity value) { if (activity == value) activity = null; }
    @Override public void onActivityCreated(Activity value, Bundle state) {}
    @Override public void onActivityPaused(Activity value) {}
    @Override public void onActivityStopped(Activity value) {}
    @Override public void onActivitySaveInstanceState(Activity value, Bundle state) {}
}
