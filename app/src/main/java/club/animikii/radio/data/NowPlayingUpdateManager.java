package club.animikii.radio.data;

import android.os.SystemClock;
import android.util.Log;
import club.animikii.radio.core.AzuraCastParser.NowPlaying;
import club.animikii.radio.core.NowPlayingRequestTracker;
import club.animikii.radio.core.StationRoutes;
import club.animikii.radio.core.TrackProgress;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Shares one Now Playing event stream between the foreground UI and background player service. */
public final class NowPlayingUpdateManager {
    private static final String TAG = "NowPlayingUpdates";
    private static final long FALLBACK_POLL_INTERVAL_MS = 30_000L;
    private static final long INITIAL_RECONNECT_DELAY_MS = 1_000L;
    private static final long MAX_RECONNECT_DELAY_MS = 30_000L;
    private static final int MAX_SSE_FRAME_CHARS = 2 * 1024 * 1024;

    private static final NowPlayingUpdateManager INSTANCE =
            new NowPlayingUpdateManager(new AzuraCastRepository());

    public interface Listener {
        void onNowPlaying(NowPlaying nowPlaying);
    }

    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private static final class ListenerRegistration {
        final Listener listener;
        final Object callbackLock = new Object();
        boolean active = true;

        ListenerRegistration(Listener listener) {
            this.listener = listener;
        }
    }

    private static final class PendingDispatch {
        final long generation;
        final List<ListenerRegistration> targets;
        final NowPlaying update;

        PendingDispatch(long generation, List<ListenerRegistration> targets, NowPlaying update) {
            this.generation = generation;
            this.targets = targets;
            this.update = update;
        }
    }

    private final Object stateLock = new Object();
    private final Set<ListenerRegistration> listeners = new HashSet<>();
    private final NowPlayingSource source;
    private final AzuraCastSseParser sseParser = new AzuraCastSseParser();
    private final TrackProgress trackProgress = new TrackProgress();
    private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor(
            daemonThreadFactory("animikii-now-playing-sse"));
    private final ExecutorService fallbackExecutor = Executors.newSingleThreadExecutor(
            daemonThreadFactory("animikii-now-playing-rest"));
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            daemonThreadFactory("animikii-now-playing-scheduler"));
    private final ExecutorService callbackExecutor = Executors.newSingleThreadExecutor(
            daemonThreadFactory("animikii-now-playing-callbacks"));

    private volatile NowPlaying latest;
    private volatile boolean active;
    private volatile boolean sseConnected;
    private volatile HttpURLConnection eventConnection;
    private long generation;
    private boolean fallbackFetchInFlight;
    private long fallbackRequestSequence;
    private Future<?> fallbackRequestFuture;
    private Future<?> streamFuture;
    private ScheduledFuture<?> fallbackFuture;
    private PendingDispatch pendingDispatch;
    private boolean callbackDispatchScheduled;

    NowPlayingUpdateManager(NowPlayingSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.source = source;
    }

    public static NowPlayingUpdateManager getInstance() {
        return INSTANCE;
    }

    public TrackProgress getTrackProgress() {
        return trackProgress;
    }

    public Subscription subscribe(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        ListenerRegistration registration = new ListenerRegistration(listener);
        boolean start = false;
        long runGeneration;
        synchronized (stateLock) {
            listeners.add(registration);
            if (listeners.size() == 1 && !active) {
                active = true;
                sseConnected = false;
                fallbackFetchInFlight = false;
                generation++;
                start = true;
            }
            runGeneration = generation;
            NowPlaying cached = latest;
            if (cached != null) {
                enqueueDispatchLocked(runGeneration,
                        Collections.singletonList(registration), cached);
            }
        }

        if (start) {
            startEventStream(runGeneration);
        }

        return new Subscription() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (this) {
                    if (closed) {
                        return;
                    }
                    closed = true;
                }
                unsubscribe(registration);
            }
        };
    }

    private void startEventStream(long runGeneration) {
        Future<?> future = streamExecutor.submit(() -> runEventStream(runGeneration));
        synchronized (stateLock) {
            if (isActiveLocked(runGeneration)) {
                streamFuture = future;
            } else {
                future.cancel(true);
            }
        }
    }

    private void runEventStream(long runGeneration) {
        long retryDelayMs = INITIAL_RECONNECT_DELAY_MS;
        while (isActive(runGeneration)) {
            HttpURLConnection connection = null;
            try {
                connection = source.openNowPlayingEventStream();
                synchronized (stateLock) {
                    if (!isActiveLocked(runGeneration)) {
                        connection.disconnect();
                        return;
                    }
                    eventConnection = connection;
                    sseConnected = true;
                    cancelFallbackLocked();
                    cancelFallbackRequestLocked();
                }

                try (BoundedSseLineReader reader = new BoundedSseLineReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8),
                        MAX_SSE_FRAME_CHARS)) {
                    StringBuilder frame = new StringBuilder();
                    String line;
                    while (isActive(runGeneration) && (line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (frame.length() > 0) {
                                List<NowPlaying> updates = sseParser.parseFrame(
                                        frame.toString(), StationRoutes.STATION_SHORTCODE);
                                frame.setLength(0);
                                for (NowPlaying update : updates) {
                                    applyUpdate(runGeneration, nextRequestId(), update);
                                }
                                if (!updates.isEmpty()) {
                                    retryDelayMs = INITIAL_RECONNECT_DELAY_MS;
                                }
                            }
                        } else if (!line.startsWith(":")) {
                            if (frame.length() + line.length() > MAX_SSE_FRAME_CHARS) {
                                throw new IOException("AzuraCast SSE frame exceeded the size limit");
                            }
                            if (frame.length() > 0) {
                                frame.append('\n');
                            }
                            frame.append(line);
                        }
                    }
                }
                if (isActive(runGeneration)) {
                    throw new IOException("AzuraCast SSE connection closed");
                }
            } catch (IOException | RuntimeException error) {
                if (isActive(runGeneration)) {
                    Log.w(TAG, "SSE unavailable; REST fallback is active ("
                            + error.getClass().getSimpleName() + ")");
                }
            } finally {
                synchronized (stateLock) {
                    if (eventConnection == connection) {
                        eventConnection = null;
                    }
                    if (isActiveLocked(runGeneration)) {
                        sseConnected = false;
                        scheduleFallbackLocked(runGeneration, 0L);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (isActive(runGeneration)) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                retryDelayMs = Math.min(retryDelayMs * 2L, MAX_RECONNECT_DELAY_MS);
            }
        }
    }

    private void scheduleFallbackLocked(long runGeneration, long initialDelayMs) {
        if (!isActiveLocked(runGeneration) || sseConnected
                || (fallbackFuture != null && !fallbackFuture.isDone())) {
            return;
        }
        fallbackFuture = scheduler.scheduleWithFixedDelay(
                () -> fetchFallback(runGeneration), initialDelayMs,
                FALLBACK_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelFallbackLocked() {
        if (fallbackFuture != null) {
            fallbackFuture.cancel(false);
            fallbackFuture = null;
        }
    }

    private void cancelFallbackRequestLocked() {
        fallbackRequestSequence++;
        fallbackFetchInFlight = false;
        if (fallbackRequestFuture != null) {
            fallbackRequestFuture.cancel(true);
            fallbackRequestFuture = null;
        }
    }

    private void fetchFallback(long runGeneration) {
        long requestSequence;
        synchronized (stateLock) {
            if (!isActiveLocked(runGeneration) || sseConnected || fallbackFetchInFlight) {
                return;
            }
            fallbackFetchInFlight = true;
            requestSequence = ++fallbackRequestSequence;
        }
        long requestId = nextRequestId();
        try {
            Future<?> future = fallbackExecutor.submit(() -> {
                try {
                    if (isFallbackRequestActive(runGeneration, requestSequence)) {
                        applyUpdate(runGeneration, requestId, source.fetchNowPlaying());
                    }
                } catch (IOException | RuntimeException error) {
                    if (isActive(runGeneration)) {
                        Log.w(TAG, "REST Now Playing fallback failed: "
                                + error.getClass().getSimpleName());
                    }
                } finally {
                    synchronized (stateLock) {
                        if (generation == runGeneration
                                && fallbackRequestSequence == requestSequence) {
                            fallbackFetchInFlight = false;
                            fallbackRequestFuture = null;
                        }
                    }
                }
            });
            synchronized (stateLock) {
                if (generation == runGeneration
                        && fallbackRequestSequence == requestSequence
                        && fallbackFetchInFlight && !sseConnected) {
                    fallbackRequestFuture = future;
                } else {
                    future.cancel(true);
                }
            }
        } catch (RuntimeException rejected) {
            synchronized (stateLock) {
                if (generation == runGeneration
                        && fallbackRequestSequence == requestSequence) {
                    fallbackFetchInFlight = false;
                    fallbackRequestFuture = null;
                }
            }
        }
    }

    private boolean isFallbackRequestActive(long runGeneration, long requestSequence) {
        synchronized (stateLock) {
            return isActiveLocked(runGeneration) && !sseConnected
                    && fallbackRequestSequence == requestSequence;
        }
    }

    private long nextRequestId() {
        return NowPlayingRequestTracker.INSTANCE.beginRequest();
    }

    private void applyUpdate(long runGeneration, long requestId, NowPlaying update) {
        if (update == null) {
            return;
        }
        synchronized (stateLock) {
            if (!isActiveLocked(runGeneration)
                    || !NowPlayingRequestTracker.INSTANCE.tryApply(requestId)) {
                return;
            }
            latest = update;
            trackProgress.update(update.getPlaybackId(), update.getDurationSeconds(),
                    update.getElapsedSeconds(), SystemClock.elapsedRealtime());
            List<ListenerRegistration> targets = new ArrayList<>(listeners);
            enqueueDispatchLocked(runGeneration, targets, update);
        }
    }

    private void enqueueDispatchLocked(long runGeneration, List<ListenerRegistration> targets,
                                       NowPlaying update) {
        Set<ListenerRegistration> activeTargets = new HashSet<>();
        if (pendingDispatch != null && pendingDispatch.generation == runGeneration) {
            for (ListenerRegistration registration : pendingDispatch.targets) {
                if (listeners.contains(registration)) {
                    activeTargets.add(registration);
                }
            }
        }
        for (ListenerRegistration registration : targets) {
            if (listeners.contains(registration)) {
                activeTargets.add(registration);
            }
        }
        pendingDispatch = new PendingDispatch(runGeneration,
                new ArrayList<>(activeTargets), update);
        if (!callbackDispatchScheduled) {
            callbackDispatchScheduled = true;
            callbackExecutor.execute(this::drainPendingDispatches);
        }
    }

    private void drainPendingDispatches() {
        while (true) {
            PendingDispatch dispatch;
            synchronized (stateLock) {
                dispatch = pendingDispatch;
                pendingDispatch = null;
                if (dispatch == null) {
                    callbackDispatchScheduled = false;
                    return;
                }
            }
            notifyListeners(dispatch.generation, dispatch.targets, dispatch.update);
        }
    }

    private void notifyListeners(long runGeneration, List<ListenerRegistration> targets,
                                 NowPlaying update) {
        for (ListenerRegistration registration : targets) {
            notifyRegistration(runGeneration, registration, update);
        }
    }

    private void notifyRegistration(long runGeneration, ListenerRegistration registration,
                                    NowPlaying update) {
        synchronized (registration.callbackLock) {
            synchronized (stateLock) {
                if (!registration.active || !isActiveLocked(runGeneration)
                        || !listeners.contains(registration)) {
                    return;
                }
            }
            try {
                registration.listener.onNowPlaying(update);
            } catch (RuntimeException error) {
                Log.w(TAG, "A Now Playing listener failed: " + error.getClass().getSimpleName());
            }
        }
    }

    private void unsubscribe(ListenerRegistration registration) {
        HttpURLConnection connection = null;
        Future<?> stream = null;
        boolean stop = false;
        synchronized (registration.callbackLock) {
            if (!registration.active) {
                return;
            }
            registration.active = false;
            synchronized (stateLock) {
                listeners.remove(registration);
                if (listeners.isEmpty() && active) {
                    active = false;
                    generation++;
                    sseConnected = false;
                    latest = null;
                    trackProgress.clear();
                    connection = eventConnection;
                    eventConnection = null;
                    stream = streamFuture;
                    streamFuture = null;
                    cancelFallbackLocked();
                    cancelFallbackRequestLocked();
                    pendingDispatch = null;
                    stop = true;
                }
            }
        }
        if (!stop) {
            return;
        }
        if (connection != null) {
            connection.disconnect();
        }
        if (stream != null) {
            stream.cancel(true);
        }
    }

    private boolean isActive(long runGeneration) {
        synchronized (stateLock) {
            return isActiveLocked(runGeneration);
        }
    }

    private boolean isActiveLocked(long runGeneration) {
        return active && generation == runGeneration && !listeners.isEmpty();
    }

    private static ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
