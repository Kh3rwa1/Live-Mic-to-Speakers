package demo.ads;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import java.lang.ref.WeakReference;

/** Placement-owned native/banner resources; per-show callbacks; no global Activity or Dialog. */
public final class GoogleAds {
    private static final GoogleAds INSTANCE = new GoogleAds();
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private boolean loadingInterstitial, loadingRewarded;
    private long interstitialAt, rewardedAt, lastShown = -60_000, epoch;
    private GoogleAds() {
        AdConsent.observe(() -> {
            if (!AdsHandler.isAdsOn()) {
                epoch++; interstitial = null; rewarded = null;
                loadingInterstitial = false; loadingRewarded = false;
            }
        });
    }
    public static GoogleAds getInstance() { return INSTANCE; }
    public static boolean checkConnection(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo network = manager == null ? null : manager.getActiveNetworkInfo();
            return network != null && network.isConnected();
        } catch (RuntimeException error) { return false; }
    }
    private static boolean validId(String id) { return id != null && !id.trim().isEmpty() && !"0".equals(id); }
    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }
    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && activity instanceof LifecycleOwner;
    }
    public boolean admobBanner(Context context, View view) { return bind(context, view, 0); }
    public boolean admobBanner90(Context context, View view) { return bind(context, view, 0); }
    public boolean addNativeView(Context context, View view) { return bind(context, view, R.layout.small_ad_unified); }
    public boolean addBigNativeView(Context context, View view) { return bind(context, view, R.layout.big_ad_unified); }
    public boolean addNativeAdvanceView(Context context, View view) { return addBigNativeView(context, view); }
    private boolean bind(Context context, View view, int layout) {
        Activity host = activity(context);
        if (!(view instanceof ViewGroup) || !usable(host)) { if (view != null) view.setVisibility(View.GONE); return false; }
        Object old = view.getTag(R.id.managed_ad_placement);
        if (old instanceof Placement) ((Placement) old).close();
        Placement placement = new Placement(host, (ViewGroup) view, layout);
        view.setTag(R.id.managed_ad_placement, placement);
        placement.owner.getLifecycle().addObserver(placement);
        AdConsent.observe(placement);
        placement.run();
        return true;
    }
    private final class Placement implements DefaultLifecycleObserver, Runnable {
        Activity host;
        LifecycleOwner owner;
        ViewGroup container;
        final int layout;
        AdView banner;
        NativeAd nativeAd;
        boolean closed, loading;
        long generation;
        Placement(Activity host, ViewGroup container, int layout) {
            this.host = host; this.owner = (LifecycleOwner) host; this.container = container; this.layout = layout;
            container.setVisibility(View.GONE);
        }
        private boolean active() {
            return !closed && usable(host) && owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
                    && AdsHandler.isAdsOn();
        }
        @Override public void run() {
            if (closed) return;
            if (!AdsHandler.isAdsOn()) { clear(); return; }
            if (!active() || loading || banner != null || nativeAd != null || !checkConnection(host)) return;
            String id;
            if (layout == 0) {
                id = AdsHandler.bannerId;
            } else if (layout == R.layout.big_ad_unified) {
                id = validId(AdsHandler.nativeAdvanceId) ? AdsHandler.nativeAdvanceId : AdsHandler.nativeId;
            } else {
                id = validId(AdsHandler.nativeId) ? AdsHandler.nativeId : AdsHandler.nativeAdvanceId;
            }
            if (!validId(id)) return;
            loading = true;
            long ticket = ++generation;
            if (layout == 0) {
                AdView view = new AdView(host);
                banner = view;
                view.setAdSize(AdSize.BANNER); view.setAdUnitId(id);
                container.removeAllViews(); container.addView(view);
                view.setAdListener(new AdListener() {
                    @Override public void onAdLoaded() {
                        if (ticket != generation || closed) return;
                        loading = false;
                        if (active()) container.setVisibility(View.VISIBLE); else clear();
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) { if (ticket == generation) clear(); }
                });
                view.loadAd(new AdRequest.Builder().build());
            } else {
                new AdLoader.Builder(host.getApplicationContext(), id).forNativeAd(loaded -> {
                    if (ticket != generation || !active()) { loaded.destroy(); if (ticket == generation) loading = false; return; }
                    loading = false; nativeAd = loaded;
                    NativeAdView view = (NativeAdView) LayoutInflater.from(host).inflate(layout, container, false);
                    populate(loaded, view);
                    container.removeAllViews(); container.addView(view); container.setVisibility(View.VISIBLE);
                }).withAdListener(new AdListener() {
                    @Override public void onAdFailedToLoad(LoadAdError error) { if (ticket == generation) clear(); }
                }).build().loadAd(new AdRequest.Builder().build());
            }
            prefetchInterstitial(host.getApplicationContext());
        }
        private void clear() {
            generation++; loading = false;
            if (banner != null) { banner.destroy(); banner = null; }
            if (nativeAd != null) { nativeAd.destroy(); nativeAd = null; }
            container.removeAllViews(); container.setVisibility(View.GONE);
        }
        @Override public void onStart(LifecycleOwner ignored) { if (banner != null) banner.resume(); run(); }
        @Override public void onStop(LifecycleOwner ignored) { if (banner != null) banner.pause(); }
        @Override public void onDestroy(LifecycleOwner ignored) { close(); }
        void close() {
            if (closed) return;
            closed = true; clear(); AdConsent.removeObserver(this); owner.getLifecycle().removeObserver(this);
            if (container.getTag(R.id.managed_ad_placement) == this) container.setTag(R.id.managed_ad_placement, null);
            host = null; owner = null; container = null;
        }
    }
    private void prefetchInterstitial(Context app) {
        if (!AdsHandler.isAdsOn() || loadingInterstitial || interstitial != null || !validId(AdsHandler.interstitialId)) return;
        loadingInterstitial = true; long ticket = epoch;
        InterstitialAd.load(app, AdsHandler.interstitialId, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd value) {
                if (ticket != epoch) return;
                loadingInterstitial = false;
                if (AdsHandler.isAdsOn()) { interstitial = value; interstitialAt = SystemClock.elapsedRealtime(); }
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { if (ticket == epoch) loadingInterstitial = false; }
        });
    }
    private void prefetchRewarded(Context app) {
        if (!AdsHandler.isAdsOn() || loadingRewarded || rewarded != null || !validId(AdsHandler.rewardedId)) return;
        loadingRewarded = true; long ticket = epoch;
        RewardedAd.load(app, AdsHandler.rewardedId, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd value) {
                if (ticket != epoch) return;
                loadingRewarded = false;
                if (AdsHandler.isAdsOn()) { rewarded = value; rewardedAt = SystemClock.elapsedRealtime(); }
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { if (ticket == epoch) loadingRewarded = false; }
        });
    }
    public void showCounterInterstitialAd(Activity activity, CustomAdsListener listener) {
        Completion completion = new Completion(activity, listener);
        if (interstitial != null && SystemClock.elapsedRealtime() - interstitialAt >= 3_600_000) interstitial = null;
        if (!completion.canShow() || !AdsHandler.isAdsOn() || interstitial == null
                || SystemClock.elapsedRealtime() - lastShown < 60_000) {
            if (usable(activity)) prefetchInterstitial(activity.getApplicationContext());
            completion.finish(); return;
        }
        InterstitialAd ready = interstitial; interstitial = null; lastShown = SystemClock.elapsedRealtime();
        ready.setFullScreenContentCallback(completion);
        try { ready.show(activity); } catch (RuntimeException error) { completion.finish(); }
    }
    /** onFinish means the flow ended, never that a reward was earned. */
    public void showRewardedAd(Activity activity, CustomAdsListener listener) {
        Completion completion = new Completion(activity, listener);
        if (rewarded != null && SystemClock.elapsedRealtime() - rewardedAt >= 3_600_000) rewarded = null;
        if (!completion.canShow() || !AdsHandler.isAdsOn() || rewarded == null) {
            if (usable(activity)) prefetchRewarded(activity.getApplicationContext());
            completion.finish(); return;
        }
        RewardedAd ready = rewarded; rewarded = null;
        ready.setFullScreenContentCallback(completion);
        try { ready.show(activity, reward -> { }); } catch (RuntimeException error) { completion.finish(); }
    }
    private static final class Completion extends FullScreenContentCallback implements DefaultLifecycleObserver {
        final WeakReference<Activity> host;
        final DeferredCompletion delivery;
        Completion(Activity activity, CustomAdsListener listener) {
            host = new WeakReference<>(activity);
            delivery = new DeferredCompletion(() -> {
                Activity current = host.get();
                detach(current);
                if (usable(current) && listener != null) listener.onFinish();
            });
            if (usable(activity)) ((LifecycleOwner) activity).getLifecycle().addObserver(this);
        }
        boolean canShow() {
            Activity activity = host.get();
            return !delivery.isClosed() && usable(activity)
                    && ((LifecycleOwner) activity).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
        }
        private void detach(Activity activity) {
            if (activity instanceof LifecycleOwner) ((LifecycleOwner) activity).getLifecycle().removeObserver(this);
        }
        void finish() {
            Activity activity = host.get();
            if (!usable(activity)) { delivery.cancel(); detach(activity); return; }
            delivery.finish(canShow());
        }
        @Override public void onResume(LifecycleOwner owner) {
            // ON_RESUME is the lifecycle event authorizing delivery. A second state-snapshot
            // gate here can miss the only resume event while dispatch is still in progress.
            Activity activity = host.get();
            if (activity == owner && usable(activity)) delivery.onResume();
        }
        @Override public void onAdDismissedFullScreenContent() { finish(); }
        @Override public void onAdFailedToShowFullScreenContent(AdError error) { finish(); }
        @Override public void onDestroy(LifecycleOwner owner) { delivery.cancel(); owner.getLifecycle().removeObserver(this); }
    }
    @Deprecated public void showLoading(Activity activity, boolean cancelable) { }
    @Deprecated public void hideLoading() { }
    private static void optionalText(View view, String text) {
        if (view == null) return;
        view.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view instanceof TextView) ((TextView) view).setText(text);
    }
    private static void populate(NativeAd ad, NativeAdView view) {
        view.setMediaView(view.findViewById(R.id.ad_media));
        view.setHeadlineView(view.findViewById(R.id.ad_headline));
        view.setBodyView(view.findViewById(R.id.ad_body));
        view.setCallToActionView(view.findViewById(R.id.ad_call_to_action));
        view.setIconView(view.findViewById(R.id.ad_app_icon));
        view.setPriceView(view.findViewById(R.id.ad_price));
        view.setStarRatingView(view.findViewById(R.id.ad_stars));
        view.setStoreView(view.findViewById(R.id.ad_store));
        view.setAdvertiserView(view.findViewById(R.id.ad_advertiser));
        optionalText(view.getHeadlineView(), ad.getHeadline()); optionalText(view.getBodyView(), ad.getBody());
        optionalText(view.getCallToActionView(), ad.getCallToAction()); optionalText(view.getPriceView(), ad.getPrice());
        optionalText(view.getStoreView(), ad.getStore()); optionalText(view.getAdvertiserView(), ad.getAdvertiser());
        if (view.getMediaView() != null) view.getMediaView().setMediaContent(ad.getMediaContent());
        if (view.getIconView() != null) {
            view.getIconView().setVisibility(ad.getIcon() == null ? View.GONE : View.VISIBLE);
            if (ad.getIcon() != null) ((ImageView) view.getIconView()).setImageDrawable(ad.getIcon().getDrawable());
        }
        if (view.getStarRatingView() != null) {
            view.getStarRatingView().setVisibility(ad.getStarRating() == null ? View.GONE : View.VISIBLE);
            if (ad.getStarRating() != null) ((RatingBar) view.getStarRatingView()).setRating(ad.getStarRating().floatValue());
        }
        view.setNativeAd(ad);
    }
}
