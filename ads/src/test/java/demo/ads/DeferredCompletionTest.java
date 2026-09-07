package demo.ads;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeferredCompletionTest {
    @Test public void immediateCompletionRunsOnce() {
        int[] calls = {0}; DeferredCompletion flow = new DeferredCompletion(() -> calls[0]++);
        flow.finish(true); flow.finish(true); flow.onResume();
        assertEquals(1, calls[0]); assertTrue(flow.isClosed());
    }
    @Test public void dismissalWhilePausedWaitsForResume() {
        int[] calls = {0}; DeferredCompletion flow = new DeferredCompletion(() -> calls[0]++);
        flow.finish(false); flow.finish(false); assertEquals(0, calls[0]);
        flow.onResume(); flow.onResume(); assertEquals(1, calls[0]);
    }
    @Test public void resumeWithoutDismissalDoesNotNavigate() {
        int[] calls = {0}; DeferredCompletion flow = new DeferredCompletion(() -> calls[0]++);
        flow.onResume(); assertEquals(0, calls[0]); assertFalse(flow.isClosed());
    }
    @Test public void destructionDiscardsPendingNavigation() {
        int[] calls = {0}; DeferredCompletion flow = new DeferredCompletion(() -> calls[0]++);
        flow.finish(false); flow.cancel(); flow.onResume(); flow.finish(true);
        assertEquals(0, calls[0]); assertTrue(flow.isClosed());
    }
    @Test public void callbackCanReenterSafely() {
        int[] calls = {0}; DeferredCompletion[] flow = new DeferredCompletion[1];
        flow[0] = new DeferredCompletion(() -> { calls[0]++; flow[0].finish(true); });
        flow[0].finish(true); assertEquals(1, calls[0]);
    }
    @Test public void absentListenerIsSafe() {
        DeferredCompletion flow = new DeferredCompletion(null);
        flow.finish(false); flow.onResume(); assertTrue(flow.isClosed());
    }
}
