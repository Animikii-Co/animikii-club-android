package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class AzuraCastParserTest {
    private final AzuraCastParser parser = new AzuraCastParser();

    @Test
    public void parsesStationMetadataCurrentSongAndHistory() {
        String json = "{"
                + "\"station\":{\"name\":\"Eclectic001 Turtle Island Ojibwe Edition\","
                + "\"shortcode\":\"eclectic001\",\"listen_url\":\"https://animikii.club/listen/eclectic001/radio.mp3\","
                + "\"requests_enabled\":true},"
                + "\"listeners\":{\"current\":7,\"total\":9},"
                + "\"live\":{\"is_live\":true,\"streamer_name\":\"Host\"},"
                + "\"now_playing\":{\"played_at\":1000,\"duration\":220,\"elapsed\":42,"
                + "\"song\":{\"title\":\"Current Track\",\"artist\":\"Current Artist\","
                + "\"album\":\"Current Album\",\"art\":\"https://animikii.club/current.jpg\"}},"
                + "\"song_history\":[{\"played_at\":900,\"duration\":180,"
                + "\"song\":{\"title\":\"Previous Track\",\"artist\":\"Previous Artist\","
                + "\"album\":\"Previous Album\",\"art\":\"https://animikii.club/previous.jpg\"}}],"
                + "\"is_online\":true} ";

        AzuraCastParser.NowPlaying result = parser.parseNowPlaying(json, "eclectic001");

        assertEquals("Eclectic001 Turtle Island Ojibwe Edition", result.getStationName());
        assertEquals("Current Track", result.getTitle());
        assertEquals("Current Artist", result.getArtist());
        assertEquals("Current Album", result.getAlbum());
        assertEquals("https://animikii.club/current.jpg", result.getArtUrl());
        assertEquals("https://animikii.club/listen/eclectic001/radio.mp3", result.getStreamUrl());
        assertEquals(7, result.getListenerCount());
        assertTrue(result.isOnline());
        assertTrue(result.isLive());
        assertTrue(result.isRequestsEnabled());
        assertEquals("Host", result.getStreamerName());
        assertEquals(1, result.getHistory().size());
        assertEquals("Previous Track", result.getHistory().get(0).getTitle());
    }

    @Test
    public void selectsRequestedStationFromAllStationsArray() {
        String json = "["
                + "{\"station\":{\"shortcode\":\"other\",\"name\":\"Other\"},"
                + "\"now_playing\":{\"song\":{\"title\":\"Wrong Station\"}}},"
                + "{\"station\":{\"shortcode\":\"eclectic001\",\"name\":\"Animikii\"},"
                + "\"now_playing\":{\"song\":{\"title\":\"Right Station\",\"artist\":\"Artist\"}}}"
                + "]";

        AzuraCastParser.NowPlaying result = parser.parseNowPlaying(json, "eclectic001");

        assertEquals("Animikii", result.getStationName());
        assertEquals("Right Station", result.getTitle());
        assertEquals("Artist", result.getArtist());
    }

    @Test
    public void parsesRequestIdsAndSongMetadataWithoutRequiringLyrics() {
        String json = "[{\"request_id\":\"req-7\",\"song\":{"
                + "\"title\":\"Requested Song\",\"artist\":\"Requested Artist\","
                + "\"album\":\"Request Album\",\"art\":\"https://animikii.club/request.jpg\","
                + "\"lyrics\":\"large optional text that the app does not display\"}}]";

        List<AzuraCastParser.RequestableSong> result = parser.parseRequestableSongs(json);

        assertEquals(1, result.size());
        assertEquals("req-7", result.get(0).getRequestId());
        assertEquals("Requested Song", result.get(0).getTitle());
        assertEquals("Requested Artist", result.get(0).getArtist());
        assertEquals("Request Album", result.get(0).getAlbum());
        assertEquals("https://animikii.club/request.jpg", result.get(0).getArtUrl());
    }

    @Test
    public void toleratesMissingOptionalFieldsAndOfflineData() {
        AzuraCastParser.NowPlaying result = parser.parseNowPlaying(
                "{\"station\":{},\"now_playing\":{\"song\":{}},\"is_online\":false}",
                "eclectic001");

        assertEquals("", result.getStationName());
        assertEquals("", result.getTitle());
        assertEquals("", result.getArtist());
        assertEquals(0, result.getListenerCount());
        assertFalse(result.isOnline());
        assertFalse(result.isLive());
        assertFalse(result.isRequestsEnabled());
        assertTrue(result.getHistory().isEmpty());
    }
}
