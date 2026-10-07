package club.animikii.radio.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import club.animikii.radio.core.AzuraCastParser;
import java.util.List;
import org.junit.Test;

public class AzuraCastSseParserTest {
    private final AzuraCastSseParser parser = new AzuraCastSseParser();

    @Test
    public void parsesInitialConnectPublication() {
        String payload = "{\"connect\":{\"subs\":{\"station:eclectic001\":{"
                + "\"publications\":[{\"data\":{\"np\":" + nowPlaying("First Song")
                + "}}]}}}}";

        List<AzuraCastParser.NowPlaying> updates =
                parser.parseFrame("event: message\ndata: " + payload + "\n\n", "eclectic001");

        assertEquals(1, updates.size());
        assertEquals("First Song", updates.get(0).getTitle());
        assertEquals(7, updates.get(0).getListenerCount());
    }

    @Test
    public void parsesSubsequentPublishedUpdate() {
        String payload = "{\"pub\":{\"data\":{\"np\":" + nowPlaying("Second Song") + "}}}";

        List<AzuraCastParser.NowPlaying> updates =
                parser.parseFrame("data: " + payload + "\n\n", "eclectic001");

        assertEquals(1, updates.size());
        assertEquals("Second Song", updates.get(0).getTitle());
    }

    @Test
    public void parsesBomPrefixedCarriageReturnOnlyFrame() {
        String payload = "{\"pub\":{\"data\":{\"np\":" + nowPlaying("CR Frame") + "}}}";
        String frame = "\uFEFFevent: message\rdata: " + payload + "\r\r";

        List<AzuraCastParser.NowPlaying> updates = parser.parseFrame(frame, "eclectic001");

        assertEquals(1, updates.size());
        assertEquals("CR Frame", updates.get(0).getTitle());
    }

    @Test
    public void ignoresHeartbeatsAndOtherStationPublications() {
        String heartbeat = "event: ping\ndata: {}\n\n";
        String otherStation = "data: {\"connect\":{\"subs\":{\"station:other\":{"
                + "\"publications\":[{\"data\":{\"np\":" + nowPlaying("Wrong Station")
                + "}}]}}}}\n\n";

        assertTrue(parser.parseFrame(heartbeat, "eclectic001").isEmpty());
        assertTrue(parser.parseFrame(otherStation, "eclectic001").isEmpty());
    }

    private static String nowPlaying(String title) {
        return "{\"station\":{\"shortcode\":\"eclectic001\"},"
                + "\"listeners\":{\"current\":7},\"live\":{\"is_live\":false},"
                + "\"now_playing\":{\"sh_id\":33,\"played_at\":1000,\"duration\":180,"
                + "\"elapsed\":12,\"song\":{\"title\":\"" + title + "\","
                + "\"artist\":\"Artist\",\"album\":\"Album\"}},\"is_online\":true}";
    }
}
