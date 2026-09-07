package demo.ads;

/** Exactly-once completion that waits for a resumed owner and can release a dead owner. */
public final class DeferredCompletion {
    private Runnable callback;
    private boolean requested;
    private boolean closed;
    public DeferredCompletion(Runnable callback) { this.callback = callback; }
    public boolean isClosed() { return closed; }
    public void finish(boolean resumed) {
        if (closed) return;
        requested = true;
        if (resumed) {
            Runnable next = callback;
            cancel(); // Clear before invoking to make reentrant/duplicate SDK callbacks harmless.
            if (next != null) next.run();
        }
    }
    public void onResume() { if (requested) finish(true); }
    public void cancel() { closed = true; callback = null; }
}
