package club.animikii.radio.core;

/** Builds URLs for the station's public AzuraCast API. */
public final class StationRoutes {
    public static final String STATION_SHORTCODE = "eclectic001";
    public static final String PUBLIC_PLAYER_URL = "https://animikii.club/public/eclectic001";
    public static final String STREAM_URL = "https://animikii.club/listen/eclectic001/radio.mp3";
    private static final String API_ROOT = "https://animikii.club/api";
    private static final String REQUEST_ID_PATTERN = "[A-Za-z0-9_-]{1,64}";

    private StationRoutes() { }

    public static String nowPlayingUrl() {
        return API_ROOT + "/nowplaying/" + STATION_SHORTCODE;
    }

    public static String nowPlayingEventsUrl() {
        return API_ROOT + "/live/nowplaying/sse";
    }

    public static String requestableSongsUrl() {
        return API_ROOT + "/station/" + STATION_SHORTCODE + "/requests";
    }

    public static String songRequestUrl(String requestId) {
        if (requestId == null || !requestId.matches(REQUEST_ID_PATTERN)) {
            throw new IllegalArgumentException("Invalid AzuraCast request ID");
        }
        return API_ROOT + "/station/" + STATION_SHORTCODE + "/request/" + requestId;
    }
}
