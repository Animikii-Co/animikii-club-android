package club.animikii.radio.core;

/** Tracks visible request rows and prevents repeated loads at one scroll threshold. */
public final class RequestPagination {
    private final int pageSize;
    private int displayedCount;
    private boolean loadArmed;

    public RequestPagination(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        this.pageSize = pageSize;
    }

    public void reset() {
        displayedCount = 0;
        loadArmed = true;
    }

    /** Allows one load when the scroll reaches the prefetch zone, then rearms after leaving it. */
    public boolean onScrollNearEnd(boolean nearEnd, int totalSongs) {
        if (!nearEnd) {
            loadArmed = true;
            return false;
        }
        if (!loadArmed || displayedCount >= totalSongs) {
            return false;
        }
        loadArmed = false;
        return true;
    }

    /** Appends one page and returns its start index, or -1 if there is nothing left to show. */
    public int appendNextPage(int totalSongs) {
        if (totalSongs <= displayedCount) {
            return -1;
        }
        int startIndex = displayedCount;
        displayedCount += Math.min(pageSize, totalSongs - displayedCount);
        return startIndex;
    }

    public int getDisplayedCount() {
        return displayedCount;
    }
}
