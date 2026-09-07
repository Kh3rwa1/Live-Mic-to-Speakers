package demo.ads;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import java.lang.ref.WeakReference;

public class AppOpenManager implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private final AdsApplication application;
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private AppOpenAd ad;
    private boolean loading, showing, foregroundPending;
    private long loadedAt, lastShown = -60_000, epoch;
    public AppOpenManager(AdsApplication application) {
        this.application = application;
        application.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        AdConsent.observe(() -> { if (!AdsHandler.isAdsOn()) { epoch++; ad = null; loading = false; } });
    }
    public boolean isAdAvailable() { return ad != null && SystemClock.elapsedRealtime() - loadedAt < 4 * 60 * 60 * 1000L; }
    public void fetchAd() {
        if (loading || isAdAvailable() || !AdsHandler.isAdsOn() || AdsHandler.openAds == null
                || AdsHandler.openAds.isEmpty() || "0".equals(AdsHandler.openAds)) return;
        loading = true; long ticket = epoch;
        AppOpenAd.load(application, AdsHandler.openAds, new AdRequest.Builder().build(), new AppOpenAd.AppOpenAdLoadCallback() {
            @Override public void onAdLoaded(AppOpenAd loaded) {
                if (ticket != epoch) return;
                loading = false;
                if (AdsHandler.isAdsOn()) { ad = loaded; loadedAt = SystemClock.elapsedRealtime(); }
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { if (ticket == epoch) { loading = false; ad = null; } }
        });
    }
    public void showAdIfAvailable() {
        Activity host = activity.get();
        if (!AdsHandler.isAdsOn() || showing || !(host instanceof AdConsent.HomeScreen)
                || !(host instanceof LifecycleOwner) || host.isFinishing() || host.isDestroyed()
                || !((LifecycleOwner) host).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
        if (SystemClock.elapsedRealtime() - lastShown < 60_000) return;
        if (!isAdAvailable()) { fetchAd(); return; }
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { showing = false; ad = null; fetchAd(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { showing = false; ad = null; }
        });
        showing = true; lastShown = SystemClock.elapsedRealtime();
        try { ad.show(host); } catch (RuntimeException error) { showing = false; ad = null; }
    }
    @Override public void onStart(LifecycleOwner owner) { foregroundPending = true; }
    @Override public void onActivityResumed(Activity value) {
        if (showing) return;
        activity = new WeakReference<>(value);
        if (foregroundPending) { foregroundPending = false; showAdIfAvailable(); }
    }
    @Override public void onActivityStopped(Activity value) { if (activity.get() == value) activity.clear(); }
    @Override public void onActivityDestroyed(Activity value) { if (activity.get() == value) activity.clear(); }
    @Override public void onActivityStarted(Activity value) { }
    @Override public void onActivityCreated(Activity value, Bundle state) { }
    @Override public void onActivityPaused(Activity value) { }
    @Override public void onActivitySaveInstanceState(Activity value, Bundle state) { }
}
