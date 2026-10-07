package club.animikii.radio.data;

import club.animikii.radio.core.AzuraCastParser.NowPlaying;
import java.io.IOException;
import java.net.HttpURLConnection;

/** Injectable public Now Playing transport used by the shared update manager. */
interface NowPlayingSource {
    NowPlaying fetchNowPlaying() throws IOException;
    HttpURLConnection openNowPlayingEventStream() throws IOException;
}
