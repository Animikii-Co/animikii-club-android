package club.animikii.radio.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses public AzuraCast responses into models the app can display. */
public final class AzuraCastParser {
    public NowPlaying parseNowPlaying(String payload, String stationShortcode) {
        try {
            JsonElement root = JsonParser.parseString(payload == null ? "{}" : payload);
            JsonObject data = selectStation(root, stationShortcode);
            JsonObject station = object(data, "station");
            JsonObject listeners = object(data, "listeners");
            JsonObject live = object(data, "live");
            JsonObject current = object(data, "now_playing");
            JsonObject song = object(current, "song");
            long playbackId = longValue(current, "sh_id");
            if (playbackId == 0L) {
                playbackId = longValue(current, "played_at");
            }

            List<Song> history = new ArrayList<>();
            JsonElement historyElement = data.get("song_history");
            if (historyElement != null && historyElement.isJsonArray()) {
                for (JsonElement row : historyElement.getAsJsonArray()) {
                    if (row != null && row.isJsonObject()) {
                        JsonObject historyRow = row.getAsJsonObject();
                        history.add(readSong(object(historyRow, "song"),
                                longValue(historyRow, "played_at"),
                                doubleValue(historyRow, "duration")));
                    }
                }
            }

            return new NowPlaying(
                    string(station, "name"),
                    string(station, "shortcode"),
                    string(station, "description"),
                    string(station, "listen_url"),
                    string(station, "public_player_url"),
                    string(song, "title"),
                    string(song, "artist"),
                    string(song, "album"),
                    string(song, "art"),
                    bool(data, "is_online"),
                    bool(live, "is_live"),
                    bool(station, "requests_enabled"),
                    intValue(listeners, "current", intValue(listeners, "total", 0)),
                    string(live, "streamer_name"), playbackId,
                    doubleValue(current, "duration"),
                    doubleValue(current, "elapsed"),
                    history);
        } catch (JsonParseException | IllegalStateException exception) {
            return NowPlaying.empty();
        }
    }

    public List<RequestableSong> parseRequestableSongs(String payload) {
        try {
            JsonElement root = JsonParser.parseString(payload == null ? "[]" : payload);
            if (!root.isJsonArray()) {
                return Collections.emptyList();
            }

            List<RequestableSong> songs = new ArrayList<>();
            JsonArray rows = root.getAsJsonArray();
            for (JsonElement row : rows) {
                if (row == null || !row.isJsonObject()) {
                    continue;
                }
                JsonObject item = row.getAsJsonObject();
                String requestId = string(item, "request_id");
                JsonObject song = object(item, "song");
                if (!requestId.isEmpty() && !string(song, "title").isEmpty()) {
                    songs.add(new RequestableSong(requestId, string(song, "title"),
                            string(song, "artist"), string(song, "album"),
                            string(song, "art")));
                }
            }
            return Collections.unmodifiableList(songs);
        } catch (JsonParseException | IllegalStateException exception) {
            return Collections.emptyList();
        }
    }

    private static JsonObject selectStation(JsonElement root, String stationShortcode) {
        if (root != null && root.isJsonArray()) {
            JsonArray stations = root.getAsJsonArray();
            JsonObject first = new JsonObject();
            for (JsonElement element : stations) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject candidate = element.getAsJsonObject();
                if (first.size() == 0) {
                    first = candidate;
                }
                if (stationShortcode != null
                        && stationShortcode.equals(string(object(candidate, "station"), "shortcode"))) {
                    return candidate;
                }
            }
            return first;
        }
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
    }

    private static Song readSong(JsonObject song, long playedAt, double duration) {
        return new Song(string(song, "title"), string(song, "artist"),
                string(song, "album"), string(song, "art"), playedAt, duration);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || value.isJsonNull()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || value.isJsonNull()) {
            return 0L;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static double doubleValue(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || value.isJsonNull()) {
            return 0d;
        }
        try {
            return value.getAsDouble();
        } catch (RuntimeException ignored) {
            return 0d;
        }
    }

    public static class Song {
        private final String title;
        private final String artist;
        private final String album;
        private final String artUrl;
        private final long playedAt;
        private final double durationSeconds;

        private Song(String title, String artist, String album, String artUrl,
                     long playedAt, double durationSeconds) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artUrl = artUrl;
            this.playedAt = playedAt;
            this.durationSeconds = durationSeconds;
        }

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public String getArtUrl() { return artUrl; }
        public long getPlayedAt() { return playedAt; }
        public double getDurationSeconds() { return durationSeconds; }
    }

    public static final class RequestableSong extends Song {
        private final String requestId;

        private RequestableSong(String requestId, String title, String artist,
                                String album, String artUrl) {
            super(title, artist, album, artUrl, 0L, 0d);
            this.requestId = requestId;
        }

        public String getRequestId() { return requestId; }
    }

    public static final class NowPlaying {
        private final String stationName;
        private final String stationShortcode;
        private final String description;
        private final String streamUrl;
        private final String publicPlayerUrl;
        private final String title;
        private final String artist;
        private final String album;
        private final String artUrl;
        private final boolean online;
        private final boolean live;
        private final boolean requestsEnabled;
        private final int listenerCount;
        private final String streamerName;
        private final long playbackId;
        private final double durationSeconds;
        private final double elapsedSeconds;
        private final List<Song> history;

        private NowPlaying(String stationName, String stationShortcode, String description,
                           String streamUrl, String publicPlayerUrl, String title,
                           String artist, String album, String artUrl, boolean online,
                           boolean live, boolean requestsEnabled, int listenerCount,
                           String streamerName, long playbackId, double durationSeconds,
                           double elapsedSeconds,
                           List<Song> history) {
            this.stationName = stationName;
            this.stationShortcode = stationShortcode;
            this.description = description;
            this.streamUrl = streamUrl;
            this.publicPlayerUrl = publicPlayerUrl;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artUrl = artUrl;
            this.online = online;
            this.live = live;
            this.requestsEnabled = requestsEnabled;
            this.listenerCount = listenerCount;
            this.streamerName = streamerName;
            this.playbackId = playbackId;
            this.durationSeconds = durationSeconds;
            this.elapsedSeconds = elapsedSeconds;
            this.history = Collections.unmodifiableList(new ArrayList<>(history));
        }

        private static NowPlaying empty() {
            return new NowPlaying("", "", "", "", "", "", "", "", "",
                    false, false, false, 0, "", 0L, 0d, 0d, Collections.emptyList());
        }

        public String getStationName() { return stationName; }
        public String getStationShortcode() { return stationShortcode; }
        public String getDescription() { return description; }
        public String getStreamUrl() { return streamUrl; }
        public String getPublicPlayerUrl() { return publicPlayerUrl; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public String getArtUrl() { return artUrl; }
        public boolean isOnline() { return online; }
        public boolean isLive() { return live; }
        public boolean isRequestsEnabled() { return requestsEnabled; }
        public int getListenerCount() { return listenerCount; }
        public String getStreamerName() { return streamerName; }
        public long getPlaybackId() { return playbackId; }
        public double getDurationSeconds() { return durationSeconds; }
        public double getElapsedSeconds() { return elapsedSeconds; }
        public List<Song> getHistory() { return history; }
    }
}
