package club.animikii.radio.core;

import java.util.function.Consumer;

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
        return tryApply(requestId, null, null);
    }

    /** Applies a response value atomically with accepting its request ID. */
    public synchronized <T> boolean tryApply(long requestId, T response, Consumer<T> accept) {
        if (requestId <= latestAppliedRequestId || requestId <= 0
                || requestId > issuedRequestId) {
            return false;
        }
        if (accept != null) {
            accept.accept(response);
        }
        latestAppliedRequestId = requestId;
        return true;
    }
}
