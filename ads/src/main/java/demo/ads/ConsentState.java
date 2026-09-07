package demo.ads;

/** Fail-closed readiness policy, independent of Android and covered by JVM tests. */
public final class ConsentState {
    private volatile boolean consentAllowsAds;
    private volatile boolean sdkReady;
    public void consentResolved(boolean allowed) { consentAllowsAds = allowed; }
    public void sdkInitialized() { sdkReady = true; }
    public boolean canRequestAds() { return consentAllowsAds && sdkReady; }
}
