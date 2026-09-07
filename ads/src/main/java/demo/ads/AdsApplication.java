package demo.ads;

import android.app.Application;

public class AdsApplication extends Application {
    public static final String TAG = "AdsApplication";
    public static AdsApplication instance;
    public static AppOpenManager appOpenManager;
    public static synchronized AdsApplication getInstance() { return instance; }
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        AdsHandler.getInstance(this);
        // AdConsent initializes Mobile Ads only after the UMP readiness gate opens.
    }
}
