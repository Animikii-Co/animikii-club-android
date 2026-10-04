package club.animikii.radio.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NowPlayingRequestTrackerTest {
    @Test
    public void rejectsAnOlderResponseAfterANewerResponseHasBeenApplied() {
        NowPlayingRequestTracker tracker = new NowPlayingRequestTracker();
        long olderRequest = tracker.beginRequest();
        long newerRequest = tracker.beginRequest();

        assertTrue(tracker.tryApply(newerRequest));
        assertFalse(tracker.tryApply(olderRequest));
    }

    @Test
    public void allowsOlderResponseUntilANewerResponseSucceeds() {
        NowPlayingRequestTracker tracker = new NowPlayingRequestTracker();
        long olderRequest = tracker.beginRequest();
        long newerRequest = tracker.beginRequest();

        assertTrue(tracker.tryApply(olderRequest));
        assertTrue(tracker.tryApply(newerRequest));
    }

    @Test
    public void doesNotApplyTheSameResponseTwice() {
        NowPlayingRequestTracker tracker = new NowPlayingRequestTracker();
        long request = tracker.beginRequest();

        assertTrue(tracker.tryApply(request));
        assertFalse(tracker.tryApply(request));
    }
}
