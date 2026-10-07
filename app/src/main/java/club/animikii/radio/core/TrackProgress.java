package club.animikii.radio.core;

import java.util.Locale;

/** Estimates a live track's position between server Now Playing updates. */
public final class TrackProgress {
    public static final int PROGRESS_MAX = 1_000;

    private long playbackId;
    private long durationMs;
    private long anchorPositionMs;
    private long anchorElapsedRealtimeMs;

    /**
     * Updates the server-reported position. For the same play instance, stale responses never
     * rewind the displayed progress. {@code elapsedRealtimeMs} must use a monotonic clock.
     */
    public synchronized void update(long newPlaybackId, double durationSeconds,
                                    double elapsedSeconds, long elapsedRealtimeMs) {
        long newDurationMs = secondsToMillis(durationSeconds);
        if (newDurationMs <= 0L) {
            clear();
            return;
        }

        long newPositionMs = clamp(secondsToMillis(elapsedSeconds), 0L, newDurationMs);
        boolean samePlayback = newPlaybackId != 0L && newPlaybackId == playbackId;
        if (!samePlayback) {
            playbackId = newPlaybackId;
            durationMs = newDurationMs;
            anchorPositionMs = newPositionMs;
            anchorElapsedRealtimeMs = elapsedRealtimeMs;
            return;
        }

        long currentPositionMs = Math.min(positionAt(elapsedRealtimeMs), newDurationMs);
        anchorPositionMs = Math.max(currentPositionMs, newPositionMs);
        durationMs = newDurationMs;
        anchorElapsedRealtimeMs = elapsedRealtimeMs;
    }

    public synchronized void clear() {
        playbackId = 0L;
        durationMs = 0L;
        anchorPositionMs = 0L;
        anchorElapsedRealtimeMs = 0L;
    }

    public synchronized boolean hasProgress() {
        return durationMs > 0L;
    }

    public synchronized long getDurationMs() {
        return durationMs;
    }

    public synchronized long getPositionMs(long elapsedRealtimeMs) {
        return durationMs <= 0L ? 0L : positionAt(elapsedRealtimeMs);
    }

    public synchronized int getProgressPermille(long elapsedRealtimeMs) {
        if (durationMs <= 0L) {
            return 0;
        }
        return (int) Math.round((double) positionAt(elapsedRealtimeMs) * PROGRESS_MAX
                / durationMs);
    }

    private long positionAt(long elapsedRealtimeMs) {
        long elapsedSinceAnchorMs = Math.max(0L, elapsedRealtimeMs - anchorElapsedRealtimeMs);
        long remainingMs = durationMs - anchorPositionMs;
        return elapsedSinceAnchorMs >= remainingMs
                ? durationMs : anchorPositionMs + elapsedSinceAnchorMs;
    }

    private static long secondsToMillis(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            return 0L;
        }
        double millis = seconds * 1_000d;
        return millis >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(millis);
    }

    private static long clamp(long value, long min, long max) {
        return Math.min(max, Math.max(min, value));
    }

    public static String formatTime(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, seconds);
    }
}
