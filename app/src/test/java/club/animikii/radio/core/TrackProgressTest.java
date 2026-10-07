package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrackProgressTest {
    @Test
    public void startsAtServerElapsedAndAdvancesFromMonotonicClock() {
        TrackProgress progress = new TrackProgress();
        progress.update(10L, 197d, 87d, 10_000L);

        assertTrue(progress.hasProgress());
        assertEquals(197_000L, progress.getDurationMs());
        assertEquals(87_000L, progress.getPositionMs(10_000L));
        assertEquals(90_500L, progress.getPositionMs(13_500L));
        assertEquals(459, progress.getProgressPermille(13_500L));
    }

    @Test
    public void staleSameTrackUpdatesNeverMoveTheBarBackward() {
        TrackProgress progress = new TrackProgress();
        progress.update(10L, 197d, 87d, 10_000L);

        progress.update(10L, 197d, 88d, 12_000L);

        assertEquals(89_000L, progress.getPositionMs(12_000L));
    }

    @Test
    public void durationCorrectionForSamePlaybackDoesNotRewindProgress() {
        TrackProgress progress = new TrackProgress();
        progress.update(10L, 197d, 87d, 10_000L);

        progress.update(10L, 198d, 88d, 12_000L);

        assertEquals(89_000L, progress.getPositionMs(12_000L));
        assertEquals(198_000L, progress.getDurationMs());
    }

    @Test
    public void newTrackResetsAndProgressStopsAtItsDuration() {
        TrackProgress progress = new TrackProgress();
        progress.update(10L, 197d, 120d, 10_000L);
        progress.update(11L, 180d, 2d, 20_000L);

        assertEquals(2_000L, progress.getPositionMs(20_000L));
        assertEquals(180_000L, progress.getPositionMs(300_000L));
        assertEquals(1_000, progress.getProgressPermille(300_000L));
    }

    @Test
    public void missingOrInvalidDurationDisablesProgress() {
        TrackProgress progress = new TrackProgress();
        progress.update(0L, 0d, 0d, 10_000L);

        assertFalse(progress.hasProgress());
        assertEquals(0L, progress.getDurationMs());
        assertEquals(0, progress.getProgressPermille(11_000L));
    }

    @Test
    public void veryLargeTrackMetadataCannotOverflowProgressBackToZero() {
        TrackProgress progress = new TrackProgress();
        progress.update(99L, Double.MAX_VALUE, Double.MAX_VALUE, 10L);

        assertEquals(Long.MAX_VALUE, progress.getDurationMs());
        assertEquals(Long.MAX_VALUE, progress.getPositionMs(20L));
        assertEquals(1_000, progress.getProgressPermille(20L));
    }

    @Test
    public void formatsElapsedAndDurationForTheTimeBar() {
        assertEquals("3:17", TrackProgress.formatTime(197_000L));
        assertEquals("1:02:03", TrackProgress.formatTime(3_723_000L));
        assertEquals("0:00", TrackProgress.formatTime(-1L));
    }
}
