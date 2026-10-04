package club.animikii.radio.playback;

/** Calculates progressively longer waits between failed stream reconnect attempts. */
public final class ReconnectBackoff {
    private static final long INITIAL_DELAY_MS = 2_000L;
    private static final long MAX_DELAY_MS = 60_000L;
    private int consecutiveFailures;

    public long nextDelayMillis() {
        int exponent = Math.min(consecutiveFailures, 5);
        consecutiveFailures++;
        return Math.min(INITIAL_DELAY_MS << exponent, MAX_DELAY_MS);
    }

    public void reset() {
        consecutiveFailures = 0;
    }
}
