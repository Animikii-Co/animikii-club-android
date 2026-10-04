package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RequestPaginationTest {
    @Test
    public void initialAndSubsequentPagesContainAtMostTwentySongs() {
        RequestPagination pagination = new RequestPagination(20);
        pagination.reset();

        assertEquals(0, pagination.appendNextPage(2054));
        assertEquals(20, pagination.getDisplayedCount());
        assertTrue(pagination.onScrollNearEnd(true, 2054));
        assertEquals(20, pagination.appendNextPage(2054));
        assertEquals(40, pagination.getDisplayedCount());
    }

    @Test
    public void repeatedScrollEventsNearEndOnlyTriggerOnePageUntilLeavingThreshold() {
        RequestPagination pagination = new RequestPagination(20);
        pagination.reset();
        pagination.appendNextPage(2054);
        assertTrue(pagination.onScrollNearEnd(true, 2054));
        pagination.appendNextPage(2054);

        assertFalse(pagination.onScrollNearEnd(true, 2054));
        assertFalse(pagination.onScrollNearEnd(true, 2054));
        assertEquals(40, pagination.getDisplayedCount());

        assertFalse(pagination.onScrollNearEnd(false, 2054));
        assertTrue(pagination.onScrollNearEnd(true, 2054));
        assertEquals(40, pagination.appendNextPage(2054));
        assertEquals(60, pagination.getDisplayedCount());
    }

    @Test
    public void finalPageContainsOnlyRemainingSongsAndThenStops() {
        RequestPagination pagination = new RequestPagination(20);
        pagination.reset();
        assertEquals(0, pagination.appendNextPage(45));
        assertTrue(pagination.onScrollNearEnd(true, 45));
        assertEquals(20, pagination.appendNextPage(45));
        assertFalse(pagination.onScrollNearEnd(false, 45));
        assertTrue(pagination.onScrollNearEnd(true, 45));
        assertEquals(40, pagination.appendNextPage(45));
        assertEquals(45, pagination.getDisplayedCount());
        assertFalse(pagination.onScrollNearEnd(true, 45));
        assertEquals(-1, pagination.appendNextPage(45));
    }
}
