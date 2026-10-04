package club.animikii.radio.core;

/** Orders now-playing responses shared by the Activity and playback service. */
public final class NowPlayingRequestTracker {
    public static final NowPlayingRequestTracker INSTANCE = new NowPlayingRequestTracker();

    private long issuedRequestId;
    private long latestAppliedRequestId;

    public synchronized long beginRequest() {
        return ++issuedRequestId;
    }

    /** Returns true once, and only for a response newer than every applied response. */
    public synchronized boolean tryApply(long requestId) {
        if (requestId <= latestAppliedRequestId || requestId <= 0
                || requestId > issuedRequestId) {
            return false;
        }
        latestAppliedRequestId = requestId;
        return true;
    }
}
