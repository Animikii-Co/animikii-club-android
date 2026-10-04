package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;
import org.junit.Test;

public class PlaybackButtonStateTest {
    @Test
    public void playingPlayerShowsPauseAfterTheListenScreenIsRebuilt() {
        PlaybackButtonState state = PlaybackButtonState.from(
                true, true, true, Player.STATE_READY);

        assertTrue(state.shouldShowPauseIcon());
        assertFalse(state.shouldShowReconnectSpinner());
        assertEquals("Pause live radio", state.getContentDescription());
    }

    @Test
    public void failedPlayingPlayerShowsReconnectRatherThanPlay() {
        PlaybackButtonState state = PlaybackButtonState.from(
                false, true, true, Player.STATE_IDLE);

        assertFalse(state.shouldShowPauseIcon());
        assertTrue(state.shouldShowReconnectSpinner());
        assertEquals("Reconnecting to live radio", state.getContentDescription());
    }

    @Test
    public void userPausedPlayerDoesNotShowReconnectSpinner() {
        PlaybackButtonState state = PlaybackButtonState.from(
                false, false, true, Player.STATE_BUFFERING);

        assertFalse(state.shouldShowPauseIcon());
        assertFalse(state.shouldShowReconnectSpinner());
        assertEquals("Play live radio", state.getContentDescription());
    }
}
