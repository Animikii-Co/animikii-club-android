package club.animikii.radio.playback;

import android.os.SystemClock;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import club.animikii.radio.core.TrackProgress;
import java.util.function.LongSupplier;

/** Exposes station-song progress to MediaSession while forwarding audio playback unchanged. */
@UnstableApi
public final class NowPlayingMediaSessionPlayer extends ForwardingPlayer {
    private final TrackProgress trackProgress;
    private final LongSupplier elapsedRealtimeMs;

    public NowPlayingMediaSessionPlayer(Player player, TrackProgress trackProgress) {
        this(player, trackProgress, SystemClock::elapsedRealtime);
    }

    NowPlayingMediaSessionPlayer(Player player, TrackProgress trackProgress,
                                 LongSupplier elapsedRealtimeMs) {
        super(player);
        this.trackProgress = trackProgress;
        this.elapsedRealtimeMs = elapsedRealtimeMs;
    }

    @Override
    public long getDuration() {
        long durationMs = trackProgress.getDurationMs();
        return durationMs > 0L ? durationMs : super.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return trackProgress.hasProgress()
                ? trackProgress.getPositionMs(elapsedRealtimeMs.getAsLong())
                : super.getCurrentPosition();
    }

    @Override
    public boolean isCurrentMediaItemSeekable() {
        // The bar reports the station's track progress; it must not seek the live stream.
        return false;
    }

    @Override
    public boolean isCommandAvailable(int command) {
        if (command == Player.COMMAND_SEEK_BACK
                || command == Player.COMMAND_SEEK_FORWARD
                || command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                || command == Player.COMMAND_SEEK_TO_DEFAULT_POSITION) {
            return false;
        }
        return super.isCommandAvailable(command);
    }

    @Override
    public Player.Commands getAvailableCommands() {
        return super.getAvailableCommands().buildUpon()
                .remove(Player.COMMAND_SEEK_BACK)
                .remove(Player.COMMAND_SEEK_FORWARD)
                .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .build();
    }
}
