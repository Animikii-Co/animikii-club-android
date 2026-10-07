package club.animikii.radio.playback;

import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import club.animikii.radio.core.NowPlayingMetadata;
import club.animikii.radio.core.StationRoutes;
import java.util.Objects;

/** Builds and compares the live station item used by the app and system media controls. */
public final class StationMediaItemFactory {
    private static final String PLAYBACK_ID_EXTRA = "club.animikii.radio.playback_id";

    private StationMediaItemFactory() { }

    public static MediaItem create(NowPlayingMetadata track) {
        Bundle extras = new Bundle();
        extras.putLong(PLAYBACK_ID_EXTRA, track.getPlaybackId());
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(track.getTitle())
                .setArtist(track.getArtist())
                .setAlbumTitle(track.getAlbum())
                .setArtworkUri(Uri.parse(track.getArtworkUrl()))
                .setExtras(extras);
        if (track.getDurationMs() > 0L) {
            metadata.setDurationMs(track.getDurationMs());
        }
        return new MediaItem.Builder()
                .setMediaId(StationRoutes.STATION_SHORTCODE)
                .setUri(StationRoutes.STREAM_URL)
                .setMediaMetadata(metadata.build())
                .build();
    }

    public static boolean matches(MediaItem item, NowPlayingMetadata track) {
        if (item == null) {
            return false;
        }
        MediaMetadata metadata = item.mediaMetadata;
        long currentPlaybackId = metadata.extras == null
                ? 0L : metadata.extras.getLong(PLAYBACK_ID_EXTRA, 0L);
        Long expectedDuration = track.getDurationMs() > 0L ? track.getDurationMs() : null;
        return textEquals(metadata.title, track.getTitle())
                && textEquals(metadata.artist, track.getArtist())
                && textEquals(metadata.albumTitle, track.getAlbum())
                && Objects.equals(metadata.artworkUri, Uri.parse(track.getArtworkUrl()))
                && Objects.equals(metadata.durationMs, expectedDuration)
                && currentPlaybackId == track.getPlaybackId();
    }

    private static boolean textEquals(CharSequence actual, String expected) {
        return actual != null && expected.contentEquals(actual);
    }
}
