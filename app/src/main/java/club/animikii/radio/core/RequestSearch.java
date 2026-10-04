package club.animikii.radio.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Filters songs from the station's public request list. */
public final class RequestSearch {
    private RequestSearch() { }

    public static List<AzuraCastParser.RequestableSong> filter(
            List<AzuraCastParser.RequestableSong> songs, String query, int limit) {
        if (songs == null || songs.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AzuraCastParser.RequestableSong> matches = new ArrayList<>();
        for (AzuraCastParser.RequestableSong song : songs) {
            if (song == null) {
                continue;
            }
            if (needle.isEmpty() || contains(song.getTitle(), needle)
                    || contains(song.getArtist(), needle)
                    || contains(song.getAlbum(), needle)) {
                matches.add(song);
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public static List<AzuraCastParser.RequestableSong> page(
            List<AzuraCastParser.RequestableSong> songs, int startIndex, int pageSize) {
        if (songs == null || songs.isEmpty() || startIndex < 0 || pageSize <= 0
                || startIndex >= songs.size()) {
            return Collections.emptyList();
        }
        int endIndex = (int) Math.min((long) songs.size(), (long) startIndex + pageSize);
        return Collections.unmodifiableList(new ArrayList<>(songs.subList(startIndex, endIndex)));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
