package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class StationRoutesTest {
    @Test
    public void buildsPublicNowPlayingAndRequestableSongUrls() {
        assertEquals("https://animikii.club/api/nowplaying/eclectic001",
                StationRoutes.nowPlayingUrl());
        assertEquals("https://animikii.club/api/station/eclectic001/requests",
                StationRoutes.requestableSongsUrl());
    }

    @Test
    public void buildsSongRequestUrlForOpaqueStationRequestId() {
        assertEquals("https://animikii.club/api/station/eclectic001/request/abcdef0123456789",
                StationRoutes.songRequestUrl("abcdef0123456789"));
    }

    @Test
    public void rejectsRequestIdsThatCouldEscapeTheStationPath() {
        for (String invalid : new String[] {"", "../other", "x/y", "has space", "?admin=true"}) {
            try {
                StationRoutes.songRequestUrl(invalid);
                fail("Expected invalid request id to be rejected: " + invalid);
            } catch (IllegalArgumentException expected) {
                // The input must remain one opaque path segment.
            }
        }
    }
}
