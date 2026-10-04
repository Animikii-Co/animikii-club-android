package club.animikii.radio.core;

/** Normalized track metadata used by the player and the Android media session. */
public final class NowPlayingMetadata {
    public static final String DEFAULT_TITLE = "Animikii Club · Live Radio";
    public static final String DEFAULT_ARTIST = "Eclectic001";
    public static final String DEFAULT_ALBUM = "Turtle Island Ojibwe Edition";
    public static final String DEFAULT_ARTWORK_URL =
            "https://animikii.club/static/uploads/browser_icon/192.1750185150.png";

    private final String title;
    private final String artist;
    private final String album;
    private final String artworkUrl;

    private NowPlayingMetadata(String title, String artist, String album, String artworkUrl) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.artworkUrl = artworkUrl;
    }

    public static NowPlayingMetadata fromNowPlaying(AzuraCastParser.NowPlaying nowPlaying) {
        if (nowPlaying == null) {
            return fromFields(DEFAULT_TITLE, DEFAULT_ARTIST, DEFAULT_ALBUM, DEFAULT_ARTWORK_URL);
        }
        String fallbackTitle = nowPlaying.isOnline()
                ? "Animikii Club is on air" : "The station is offline";
        return fromFields(valueOr(nowPlaying.getTitle(), fallbackTitle),
                nowPlaying.getArtist(), nowPlaying.getAlbum(), nowPlaying.getArtUrl());
    }

    public static NowPlayingMetadata fromFields(
            String title, String artist, String album, String artworkUrl) {
        return new NowPlayingMetadata(
                valueOr(title, DEFAULT_TITLE),
                valueOr(artist, DEFAULT_ARTIST),
                valueOr(album, DEFAULT_ALBUM),
                artworkOrDefault(artworkUrl));
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
}
