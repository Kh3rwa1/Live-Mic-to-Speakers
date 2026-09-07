package demo.ads;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConsentStateTest {
    @Test public void defaultsToNoAds() { assertFalse(new ConsentState().canRequestAds()); }
    @Test public void requiresBothConsentAndInitialization() {
        ConsentState state = new ConsentState();
        state.consentResolved(true); assertFalse(state.canRequestAds());
        state.sdkInitialized(); assertTrue(state.canRequestAds());
    }
    @Test public void sdkReadinessAloneDoesNotAllowAds() {
        ConsentState state = new ConsentState(); state.sdkInitialized(); assertFalse(state.canRequestAds());
    }
    @Test public void changedPrivacyChoicesCloseTheGate() {
        ConsentState state = new ConsentState(); state.sdkInitialized(); state.consentResolved(true);
        state.consentResolved(false); assertFalse(state.canRequestAds());
        state.consentResolved(true); assertTrue(state.canRequestAds());
    }
}
