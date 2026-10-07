package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NowPlayingMetadataTest {
    @Test
    public void keepsTitleArtistAlbumAndArtworkInSeparateFields() {
        NowPlayingMetadata metadata = NowPlayingMetadata.fromFields(
                "Current Track", "Current Artist", "Current Album",
                "https://animikii.club/current.jpg");

        assertEquals("Current Track", metadata.getTitle());
        assertEquals("Current Artist", metadata.getArtist());
        assertEquals("Current Album", metadata.getAlbum());
        assertEquals("https://animikii.club/current.jpg", metadata.getArtworkUrl());
    }

    @Test
    public void missingArtistAndAlbumUseTheirOwnStationPlaceholders() {
        NowPlayingMetadata metadata = NowPlayingMetadata.fromFields(
                "Current Track", "  ", "", null);

        assertEquals("Current Track", metadata.getTitle());
        assertEquals("Eclectic001", metadata.getArtist());
        assertEquals("Turtle Island Ojibwe Edition", metadata.getAlbum());
        assertEquals(NowPlayingMetadata.DEFAULT_ARTWORK_URL, metadata.getArtworkUrl());
    }

    @Test
    public void missingNowPlayingDataUsesLiveStationMetadata() {
        NowPlayingMetadata metadata = NowPlayingMetadata.fromNowPlaying(null);

        assertEquals("Animikii Club", metadata.getTitle());
        assertEquals("Eclectic001", metadata.getArtist());
        assertEquals("Turtle Island Ojibwe Edition", metadata.getAlbum());
    }

    @Test
    public void mapsAzuraCastTrackFieldsIntoMatchingMediaSlots() {
        String json = "{\"station\":{\"shortcode\":\"eclectic001\"},"
                + "\"now_playing\":{\"duration\":197,\"elapsed\":87,"
                + "\"song\":{\"title\":\"North Wind\","
                + "\"artist\":\"Kani\",\"album\":\"Story Album\","
                + "\"art\":\"https://animikii.club/north-wind.jpg\"}},"
                + "\"is_online\":true}";
        AzuraCastParser.NowPlaying current = new AzuraCastParser()
                .parseNowPlaying(json, "eclectic001");

        NowPlayingMetadata metadata = NowPlayingMetadata.fromNowPlaying(current);

        assertEquals("North Wind", metadata.getTitle());
        assertEquals("Kani", metadata.getArtist());
        assertEquals("Story Album", metadata.getAlbum());
        assertEquals("https://animikii.club/north-wind.jpg", metadata.getArtworkUrl());
        assertEquals(197_000L, metadata.getDurationMs());
    }

    @Test
    public void missingNowPlayingDurationDoesNotAdvertiseASeekBarLength() {
        NowPlayingMetadata metadata = NowPlayingMetadata.fromNowPlaying(null);

        assertEquals(0L, metadata.getDurationMs());
        assertEquals(0L, metadata.getPlaybackId());
    }

    @Test
    public void preservesPlaybackInstanceIdToResetMediaSessionProgress() {
        String json = "{\"station\":{},\"now_playing\":{\"sh_id\":321,\"duration\":197,"
                + "\"elapsed\":87,\"song\":{\"title\":\"Track\"}},\"is_online\":true}";
        AzuraCastParser.NowPlaying current = new AzuraCastParser()
                .parseNowPlaying(json, "eclectic001");

        NowPlayingMetadata metadata = NowPlayingMetadata.fromNowPlaying(current);

        assertEquals(321L, metadata.getPlaybackId());
    }

    @Test
    public void rejectsNonHttpsArtworkUrls() {
        NowPlayingMetadata metadata = NowPlayingMetadata.fromFields(
                "Current Track", "Current Artist", "Current Album",
                "http://example.com/cover.jpg");

        assertEquals(NowPlayingMetadata.DEFAULT_ARTWORK_URL, metadata.getArtworkUrl());
    }
}
