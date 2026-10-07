package club.animikii.radio.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import club.animikii.radio.core.AzuraCastParser;
import club.animikii.radio.core.AzuraCastParser.NowPlaying;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class NowPlayingUpdateManagerTest {
    @Test
    public void subscriptionsShareOneStreamAndCloseIndependentlyInUpdateOrder() throws Exception {
        FakeSource source = new FakeSource();
        NowPlayingUpdateManager manager = new NowPlayingUpdateManager(source);
        List<String> titles = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstFourUpdates = new CountDownLatch(4);
        CountDownLatch fifthUpdate = new CountDownLatch(5);
        NowPlayingUpdateManager.Listener listener = nowPlaying -> {
            titles.add(nowPlaying.getTitle());
            firstFourUpdates.countDown();
            fifthUpdate.countDown();
        };
        NowPlayingUpdateManager.Subscription first = manager.subscribe(listener);
        NowPlayingUpdateManager.Subscription second = manager.subscribe(listener);

        try {
            assertTrue(source.connected.await(2, TimeUnit.SECONDS));
            assertEquals(1, source.openCount.get());
            source.publish("First", 1L);
            source.publish("Second", 2L);
            assertTrue(firstFourUpdates.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("First", "First", "Second", "Second"), snapshot(titles));

            first.close();
            source.publish("Third", 3L);
            assertTrue(fifthUpdate.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("First", "First", "Second", "Second", "Third"),
                    snapshot(titles));
            assertEquals(1, source.openCount.get());
        } finally {
            first.close();
            second.close();
            source.close();
        }
        assertTrue(source.connection.disconnected.get());
    }

    @Test
    public void coalescesQueuedSnapshotsWhenListenerIsSlow() throws Exception {
        FakeSource source = new FakeSource();
        NowPlayingUpdateManager manager = new NowPlayingUpdateManager(source);
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        CountDownLatch slowListenerSawLatest = new CountDownLatch(1);
        CountDownLatch observerSawLatest = new CountDownLatch(1);
        List<String> slowTitles = Collections.synchronizedList(new ArrayList<>());
        NowPlayingUpdateManager.Subscription slow = manager.subscribe(nowPlaying -> {
            slowTitles.add(nowPlaying.getTitle());
            if (slowTitles.size() == 1) {
                listenerEntered.countDown();
                try {
                    releaseListener.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if ("Latest".equals(nowPlaying.getTitle())) {
                slowListenerSawLatest.countDown();
            }
        });
        NowPlayingUpdateManager.Subscription observer = manager.subscribe(nowPlaying -> {
            if ("Latest".equals(nowPlaying.getTitle())) {
                observerSawLatest.countDown();
            }
        });

        try {
            assertTrue(source.connected.await(2, TimeUnit.SECONDS));
            source.publish("First", 1L);
            assertTrue(listenerEntered.await(2, TimeUnit.SECONDS));
            for (int index = 2; index < 30; index++) {
                source.publish("Update " + index, index);
            }
            source.publish("Latest", 30L);
            source.close();
            assertTrue(source.connection.disconnectedLatch.await(2, TimeUnit.SECONDS));

            releaseListener.countDown();
            assertTrue(slowListenerSawLatest.await(2, TimeUnit.SECONDS));
            assertTrue(observerSawLatest.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("First", "Latest"), snapshot(slowTitles));
        } finally {
            releaseListener.countDown();
            slow.close();
            observer.close();
            source.close();
        }
    }

    @Test
    public void usesRestFallbackWhenEventStreamCannotConnect() throws Exception {
        String json = "{\"station\":{\"shortcode\":\"eclectic001\"},"
                + "\"now_playing\":{\"sh_id\":71,\"duration\":180,\"elapsed\":12,"
                + "\"song\":{\"title\":\"Fallback Song\"}},\"is_online\":true}";
        NowPlaying fallback = new AzuraCastParser().parseNowPlaying(json, "eclectic001");
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        NowPlayingSource source = new NowPlayingSource() {
            @Override
            public NowPlaying fetchNowPlaying() {
                calls.add("rest");
                return fallback;
            }

            @Override
            public HttpURLConnection openNowPlayingEventStream() throws IOException {
                calls.add("sse");
                throw new IOException("SSE unavailable");
            }
        };
        NowPlayingUpdateManager manager = new NowPlayingUpdateManager(source);
        CountDownLatch updateReceived = new CountDownLatch(1);
        AtomicReference<NowPlaying> received = new AtomicReference<>();
        NowPlayingUpdateManager.Subscription subscription = manager.subscribe(nowPlaying -> {
            received.set(nowPlaying);
            updateReceived.countDown();
        });

        try {
            assertTrue(updateReceived.await(2, TimeUnit.SECONDS));
            assertEquals("Fallback Song", received.get().getTitle());
            List<String> callsSnapshot = snapshot(calls);
            assertTrue(callsSnapshot.indexOf("sse") >= 0);
            assertTrue(callsSnapshot.indexOf("rest") > callsSnapshot.indexOf("sse"));
        } finally {
            subscription.close();
        }
    }

    @Test
    public void cancelsFallbackRequestWhenTheLastSubscriberCloses() throws Exception {
        BlockingSource source = new BlockingSource();
        NowPlayingUpdateManager manager = new NowPlayingUpdateManager(source);
        NowPlayingUpdateManager.Subscription subscription = manager.subscribe(nowPlaying -> { });

        try {
            assertTrue(source.fetchStarted.await(2, TimeUnit.SECONDS));
            subscription.close();
            assertTrue(source.fetchInterrupted.await(2, TimeUnit.SECONDS));
        } finally {
            source.releaseFetch.countDown();
            subscription.close();
        }
    }

    private static List<String> snapshot(List<String> titles) {
        synchronized (titles) {
            return new ArrayList<>(titles);
        }
    }

    private static String event(String title, long playbackId) {
        String np = "{\"station\":{\"shortcode\":\"eclectic001\"},"
                + "\"listeners\":{\"current\":4},\"live\":{\"is_live\":false},"
                + "\"now_playing\":{\"sh_id\":" + playbackId
                + ",\"played_at\":1000,\"duration\":180,\"elapsed\":12,"
                + "\"song\":{\"title\":\"" + title + "\"}},\"is_online\":true}";
        return "data: {\"pub\":{\"data\":{\"np\":" + np + "}}}\n\n";
    }

    private static final class FakeSource implements NowPlayingSource {
        private final AtomicInteger openCount = new AtomicInteger();
        private final CountDownLatch connected = new CountDownLatch(1);
        private PipedOutputStream output;
        private FakeConnection connection;

        @Override
        public HttpURLConnection openNowPlayingEventStream() throws IOException {
            if (openCount.incrementAndGet() != 1) {
                throw new IOException("Unexpected second event-stream connection");
            }
            PipedInputStream input = new PipedInputStream(8_192);
            output = new PipedOutputStream(input);
            connected.countDown();
            connection = new FakeConnection(input);
            return connection;
        }

        @Override
        public NowPlaying fetchNowPlaying() throws IOException {
            throw new IOException("The live SSE stream is expected to stay connected");
        }

        void publish(String title, long playbackId) throws IOException {
            output.write(event(title, playbackId).getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        void close() throws IOException {
            if (output != null) {
                output.close();
            }
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final InputStream input;
        private final AtomicBoolean disconnected = new AtomicBoolean();
        private final CountDownLatch disconnectedLatch = new CountDownLatch(1);

        FakeConnection(InputStream input) throws IOException {
            super(new URL("https://example.invalid/nowplaying/sse"));
            this.input = input;
        }

        @Override
        public int getResponseCode() {
            return HTTP_OK;
        }

        @Override
        public String getContentType() {
            return "text/event-stream; charset=utf-8";
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public void disconnect() {
            disconnected.set(true);
            disconnectedLatch.countDown();
            try {
                input.close();
            } catch (IOException ignored) {
                // Already closed by the reader.
            }
        }

        @Override
        public void connect() { }

        @Override
        public boolean usingProxy() {
            return false;
        }
    }

    private static final class BlockingSource implements NowPlayingSource {
        private final CountDownLatch fetchStarted = new CountDownLatch(1);
        private final CountDownLatch fetchInterrupted = new CountDownLatch(1);
        private final CountDownLatch releaseFetch = new CountDownLatch(1);

        @Override
        public NowPlaying fetchNowPlaying() throws IOException {
            fetchStarted.countDown();
            try {
                releaseFetch.await();
            } catch (InterruptedException interrupted) {
                fetchInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IOException("Fallback fetch interrupted", interrupted);
            }
            throw new IOException("Fallback fetch released by test");
        }

        @Override
        public HttpURLConnection openNowPlayingEventStream() throws IOException {
            throw new IOException("SSE unavailable");
        }
    }
}
