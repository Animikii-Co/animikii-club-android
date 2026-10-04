package club.animikii.radio.playback;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import club.animikii.radio.core.NowPlayingMetadata;
import club.animikii.radio.core.StationRoutes;
import java.util.Objects;

/** Builds and compares the live station item used by the app and system media controls. */
public final class StationMediaItemFactory {
    private StationMediaItemFactory() { }

    public static MediaItem create(NowPlayingMetadata track) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(track.getTitle())
                .setArtist(track.getArtist())
                .setAlbumTitle(track.getAlbum())
                .setArtworkUri(Uri.parse(track.getArtworkUrl()))
                .build();
        return new MediaItem.Builder()
                .setMediaId(StationRoutes.STATION_SHORTCODE)
                .setUri(StationRoutes.STREAM_URL)
                .setMediaMetadata(metadata)
                .build();
    }

    public static boolean matches(MediaItem item, NowPlayingMetadata track) {
        if (item == null) {
            return false;
        }
        MediaMetadata metadata = item.mediaMetadata;
        return textEquals(metadata.title, track.getTitle())
                && textEquals(metadata.artist, track.getArtist())
                && textEquals(metadata.albumTitle, track.getAlbum())
                && Objects.equals(metadata.artworkUri, Uri.parse(track.getArtworkUrl()));
    }

    private static boolean textEquals(CharSequence actual, String expected) {
        return actual != null && expected.contentEquals(actual);
    }
}
