package club.animikii.radio.core;

/** Track identity and length metadata; live elapsed position is tracked separately. */
public final class NowPlayingMetadata {
    public static final String DEFAULT_TITLE = "Animikii Club";
    public static final String DEFAULT_ARTIST = "Eclectic001";
    public static final String DEFAULT_ALBUM = "Turtle Island Ojibwe Edition";
    public static final String DEFAULT_ARTWORK_URL =
            "https://animikii.club/static/uploads/browser_icon/192.1750185150.png";

    private final String title;
    private final String artist;
    private final String album;
    private final String artworkUrl;
    private final long durationMs;
    private final long playbackId;

    private NowPlayingMetadata(String title, String artist, String album, String artworkUrl,
                               long durationMs, long playbackId) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.artworkUrl = artworkUrl;
        this.durationMs = Math.max(0L, durationMs);
        this.playbackId = playbackId;
    }

    public static NowPlayingMetadata fromNowPlaying(AzuraCastParser.NowPlaying nowPlaying) {
        if (nowPlaying == null) {
            return fromFields(DEFAULT_TITLE, DEFAULT_ARTIST, DEFAULT_ALBUM, DEFAULT_ARTWORK_URL);
        }
        String fallbackTitle = nowPlaying.isOnline()
                ? "Animikii Club is on air" : "The station is offline";
        return fromFields(valueOr(nowPlaying.getTitle(), fallbackTitle),
                nowPlaying.getArtist(), nowPlaying.getAlbum(), nowPlaying.getArtUrl(),
                secondsToMillis(nowPlaying.getDurationSeconds()), nowPlaying.getPlaybackId());
    }

    public static NowPlayingMetadata fromFields(
            String title, String artist, String album, String artworkUrl) {
        return fromFields(title, artist, album, artworkUrl, 0L);
    }

    public static NowPlayingMetadata fromFields(
            String title, String artist, String album, String artworkUrl, long durationMs) {
        return fromFields(title, artist, album, artworkUrl, durationMs, 0L);
    }

    public static NowPlayingMetadata fromFields(
            String title, String artist, String album, String artworkUrl,
            long durationMs, long playbackId) {
        return new NowPlayingMetadata(
                valueOr(title, DEFAULT_TITLE),
                valueOr(artist, DEFAULT_ARTIST),
                valueOr(album, DEFAULT_ALBUM),
                artworkOrDefault(artworkUrl), durationMs, playbackId);
    }

    private static long secondsToMillis(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            return 0L;
        }
        double millis = seconds * 1_000d;
        return millis >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(millis);
    }

    private static String artworkOrDefault(String artworkUrl) {
        String normalized = valueOr(artworkUrl, DEFAULT_ARTWORK_URL);
        return normalized.regionMatches(true, 0, "https://", 0, 8)
                ? normalized : DEFAULT_ARTWORK_URL;
    }

    private static String valueOr(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getArtworkUrl() { return artworkUrl; }
    public long getDurationMs() { return durationMs; }
    public long getPlaybackId() { return playbackId; }
}
