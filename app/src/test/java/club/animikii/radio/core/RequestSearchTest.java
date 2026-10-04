package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RequestSearchTest {
    private final AzuraCastParser parser = new AzuraCastParser();

    @Test
    public void findsTracksByArtistTitleOrAlbumWithoutCaseSensitivity() {
        List<AzuraCastParser.RequestableSong> songs = parser.parseRequestableSongs("["
                + "{\"request_id\":\"one\",\"song\":{\"title\":\"North Star\",\"artist\":\"Blue Heron\",\"album\":\"Night Sky\"}},"
                + "{\"request_id\":\"two\",\"song\":{\"title\":\"Cedar Dance\",\"artist\":\"River People\",\"album\":\"Earth Songs\"}}]");

        assertEquals("one", RequestSearch.filter(songs, "HERON", 10).get(0).getRequestId());
        assertEquals("two", RequestSearch.filter(songs, "cedar", 10).get(0).getRequestId());
        assertEquals("two", RequestSearch.filter(songs, "earth songs", 10).get(0).getRequestId());
    }

    @Test
    public void limitsResultsAndTreatsBlankQueryAsBrowse() {
        List<AzuraCastParser.RequestableSong> songs = parser.parseRequestableSongs("["
                + "{\"request_id\":\"one\",\"song\":{\"title\":\"One\"}},"
                + "{\"request_id\":\"two\",\"song\":{\"title\":\"Two\"}},"
                + "{\"request_id\":\"three\",\"song\":{\"title\":\"Three\"}}]");

        List<AzuraCastParser.RequestableSong> results = RequestSearch.filter(songs, "  ", 2);

        assertEquals(2, results.size());
        assertTrue(RequestSearch.filter(songs, "not here", 20).isEmpty());
        assertTrue(RequestSearch.filter(songs, "", 0).isEmpty());
    }

    @Test
    public void pagesThroughAllFilteredResultsWithoutGapsOrDuplicates() {
        List<AzuraCastParser.RequestableSong> songs = parser.parseRequestableSongs("["
                + "{\"request_id\":\"one\",\"song\":{\"title\":\"One\"}},"
                + "{\"request_id\":\"two\",\"song\":{\"title\":\"Two\"}},"
                + "{\"request_id\":\"three\",\"song\":{\"title\":\"Three\"}}]");

        List<AzuraCastParser.RequestableSong> firstPage = RequestSearch.page(songs, 0, 2);
        List<AzuraCastParser.RequestableSong> secondPage = RequestSearch.page(songs, 2, 2);

        assertEquals(2, firstPage.size());
        assertEquals("one", firstPage.get(0).getRequestId());
        assertEquals("two", firstPage.get(1).getRequestId());
        assertEquals(1, secondPage.size());
        assertEquals("three", secondPage.get(0).getRequestId());
        assertTrue(RequestSearch.page(songs, 4, 2).isEmpty());
    }
}
