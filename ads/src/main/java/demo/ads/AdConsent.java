package demo.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArraySet;

/** UMP is the source of consent truth. No SDK initialization or requests before consent allows it. */
public final class AdConsent {
    /** App-open ads are opt-in on non-audio landing screens only. */
    public interface HomeScreen { }
    private static final ConsentState gate = new ConsentState();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Runnable> observers = new CopyOnWriteArraySet<>();
    private static ConsentInformation information;
    private static boolean busy, attempted, initializing;
    private static WeakReference<Activity> host = new WeakReference<>(null);
    private AdConsent() { }
    public static boolean canRequestAds() { return gate.canRequestAds(); }
    public static void observe(Runnable observer) { observers.add(observer); }
    public static void removeObserver(Runnable observer) { observers.remove(observer); }
    public static void notifyAdsChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(AdConsent::notifyAdsChanged); return; }
        for (Runnable observer : observers) observer.run();
    }
    private static boolean resumed(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && activity instanceof LifecycleOwner
                && ((LifecycleOwner) activity).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    }
    public static void request(Activity activity) {
        if (!resumed(activity) || !AdsHandler.isEnabledByUser()) return;
        host = new WeakReference<>(activity);
        if (busy || attempted) return;
        busy = true;
        gate.consentResolved(false);
        notifyAdsChanged();
        Context app = activity.getApplicationContext();
        if (information == null) information = UserMessagingPlatform.getConsentInformation(app);
        information.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(), () -> {
            Activity current = host.get();
            if (!resumed(current)) { busy = false; return; } // Retry from the next resumed home screen.
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(current, error -> finish(app));
        }, error -> finish(app));
    }
    private static void finish(Context app) {
        busy = false; attempted = true;
        boolean allowed = information != null && information.canRequestAds();
        gate.consentResolved(allowed);
        if (allowed && !initializing) {
            initializing = true;
            MobileAds.initialize(app, result -> main.post(() -> {
                gate.sdkInitialized(); notifyAdsChanged();
            }));
        }
        notifyAdsChanged();
    }
    public static void showPrivacyOptions(Activity activity) {
        if (!resumed(activity) || busy) return;
        if (information == null || information.getPrivacyOptionsRequirementStatus()
                != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(activity, "Ad privacy options are not currently required or available. You can retry consent from this screen.", Toast.LENGTH_LONG).show();
            attempted = false; request(activity); return;
        }
        busy = true;
        gate.consentResolved(false); // Destroy existing placements while privacy choices are changing.
        notifyAdsChanged();
        Context app = activity.getApplicationContext();
        WeakReference<Activity> owner = new WeakReference<>(activity);
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            finish(app);
            Activity current = owner.get();
            if (error != null && resumed(current)) Toast.makeText(current, "Could not update privacy choices. Please try again.", Toast.LENGTH_LONG).show();
        });
    }
}
