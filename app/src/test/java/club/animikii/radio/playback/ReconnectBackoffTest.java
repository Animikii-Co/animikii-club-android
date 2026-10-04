package club.animikii.radio.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReconnectBackoffTest {
    @Test
    public void retryDelayDoublesUntilItCapsAtOneMinute() {
        ReconnectBackoff backoff = new ReconnectBackoff();
        long[] actual = new long[7];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = backoff.nextDelayMillis();
        }

        assertArrayEquals(new long[] {2_000L, 4_000L, 8_000L, 16_000L,
                32_000L, 60_000L, 60_000L}, actual);
    }

    @Test
    public void resetAfterSuccessfulPlaybackRestartsAtInitialDelay() {
        ReconnectBackoff backoff = new ReconnectBackoff();
        backoff.nextDelayMillis();
        backoff.nextDelayMillis();

        backoff.reset();

        assertEquals(2_000L, backoff.nextDelayMillis());
    }
}
