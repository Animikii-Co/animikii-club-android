package club.animikii.radio.data;

import club.animikii.radio.core.AzuraCastParser;
import club.animikii.radio.core.StationRoutes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Accesses the station's public AzuraCast API. */
public final class AzuraCastRepository implements NowPlayingSource {
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int SSE_READ_TIMEOUT_MS = 45_000;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final AzuraCastParser parser = new AzuraCastParser();

    @Override
    public AzuraCastParser.NowPlaying fetchNowPlaying() throws IOException {
        return parser.parseNowPlaying(get(StationRoutes.nowPlayingUrl()),
                StationRoutes.STATION_SHORTCODE);
    }

    @Override
    public HttpURLConnection openNowPlayingEventStream() throws IOException {
        String connectionData = "{\"subs\":{\"station:" + StationRoutes.STATION_SHORTCODE
                + "\":{\"recover\":true}}}";
        String url = StationRoutes.nowPlayingEventsUrl() + "?cf_connect="
                + URLEncoder.encode(connectionData, StandardCharsets.UTF_8.name());
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(SSE_READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "AnimikiiRadio-Android/1.0");

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("AzuraCast SSE endpoint returned HTTP " + status);
            }
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                    .contains("text/event-stream")) {
                throw new IOException("AzuraCast SSE endpoint returned an unexpected content type");
            }
            return connection;
        } catch (IOException error) {
            connection.disconnect();
            throw error;
        }
    }

    public List<AzuraCastParser.RequestableSong> fetchRequestableSongs() throws IOException {
        return parser.parseRequestableSongs(get(StationRoutes.requestableSongsUrl()));
    }

    public void submitSongRequest(String requestId) throws IOException {
        String url = StationRoutes.songRequestUrl(requestId);
        HttpURLConnection connection = open(url, "POST");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("AzuraCast rejected the request (HTTP " + status + ")");
            }
        } finally {
            connection.disconnect();
        }
    }

    private String get(String url) throws IOException {
        HttpURLConnection connection = open(url, "GET");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("AzuraCast returned HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) {
                        throw new IOException("AzuraCast response exceeded the size limit");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AnimikiiRadio-Android/1.0");
        return connection;
    }
}
