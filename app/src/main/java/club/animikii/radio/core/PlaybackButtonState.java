package club.animikii.radio.core;

import androidx.media3.common.Player;

/** Chooses the play button's icon and label from Media3's current state. */
public final class PlaybackButtonState {
    private final boolean showPauseIcon;
    private final boolean showReconnectSpinner;
    private final String contentDescription;

    private PlaybackButtonState(boolean showPauseIcon, boolean showReconnectSpinner,
            String contentDescription) {
        this.showPauseIcon = showPauseIcon;
        this.showReconnectSpinner = showReconnectSpinner;
        this.contentDescription = contentDescription;
    }

    public static PlaybackButtonState from(boolean isPlaying, boolean playRequested,
            boolean hasMediaItem, int playbackState) {
        boolean reconnecting = playRequested && hasMediaItem
                && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                        || playbackState == Player.STATE_ENDED);
        String description = reconnecting ? "Reconnecting to live radio"
                : isPlaying ? "Pause live radio" : "Play live radio";
        return new PlaybackButtonState(isPlaying, reconnecting, description);
    }

    public boolean shouldShowPauseIcon() {
        return showPauseIcon;
    }

    public boolean shouldShowReconnectSpinner() {
        return showReconnectSpinner;
    }

    public String getContentDescription() {
        return contentDescription;
    }
}
