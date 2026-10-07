package club.animikii.radio.data;

import club.animikii.radio.core.AzuraCastParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Extracts AzuraCast Now Playing snapshots from Server-Sent Event frames. */
public final class AzuraCastSseParser {
    private final AzuraCastParser nowPlayingParser = new AzuraCastParser();

    /** Parses one complete SSE frame, returning every Now Playing snapshot it contains. */
    public List<AzuraCastParser.NowPlaying> parseFrame(String frame, String stationShortcode) {
        if (frame == null || frame.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedFrame = frame.charAt(0) == '\uFEFF' ? frame.substring(1) : frame;
        StringBuilder data = new StringBuilder();
        for (String line : normalizedFrame.split("\\r\\n|\\r|\\n")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            if (data.length() > 0) {
                data.append('\n');
            }
            String value = line.substring("data:".length());
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            data.append(value);
        }
        return parseMessage(data.toString(), stationShortcode);
    }

    private List<AzuraCastParser.NowPlaying> parseMessage(
            String payload, String stationShortcode) {
        if (payload == null || payload.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonElement root = JsonParser.parseString(payload);
            if (!root.isJsonObject()) {
                return Collections.emptyList();
            }
            JsonObject message = root.getAsJsonObject();
            List<AzuraCastParser.NowPlaying> updates = new ArrayList<>();
            JsonObject publication = object(message, "pub");
            if (publication.size() > 0) {
                addSnapshot(publication, stationShortcode, updates);
            }

            JsonObject connect = object(message, "connect");
            if (connect.size() > 0) {
                addConnectSnapshots(connect, stationShortcode, updates);
            }
            return updates.isEmpty() ? Collections.emptyList()
                    : Collections.unmodifiableList(updates);
        } catch (JsonParseException | IllegalStateException ignored) {
            return Collections.emptyList();
        }
    }

    private void addConnectSnapshots(JsonObject connect, String stationShortcode,
                                     List<AzuraCastParser.NowPlaying> updates) {
        JsonObject subscriptions = object(connect, "subs");
        JsonObject stationSubscription = object(subscriptions, "station:" + stationShortcode);
        JsonArray publications = array(stationSubscription, "publications");
        for (JsonElement publication : publications) {
            if (publication != null && publication.isJsonObject()) {
                addSnapshot(publication.getAsJsonObject(), stationShortcode, updates);
            }
        }

        // Older AzuraCast SSE payloads supplied their initial publications in connect.data.
        JsonArray legacyData = array(connect, "data");
        for (JsonElement publication : legacyData) {
            if (publication != null && publication.isJsonObject()) {
                addSnapshot(publication.getAsJsonObject(), stationShortcode, updates);
            }
        }
    }

    private void addSnapshot(JsonObject publication, String stationShortcode,
                             List<AzuraCastParser.NowPlaying> updates) {
        JsonObject data = object(publication, "data");
        JsonObject nowPlaying = object(data, "np");
        if (nowPlaying.size() > 0) {
            updates.add(nowPlayingParser.parseNowPlaying(nowPlaying.toString(), stationShortcode));
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}
