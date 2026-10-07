package club.animikii.radio.data;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

/** Reads SSE lines with a hard character bound and supports all SSE line endings. */
final class BoundedSseLineReader implements AutoCloseable {
    private final PushbackReader reader;
    private final int maxLineLength;
    private boolean skipLineFeed;

    BoundedSseLineReader(Reader reader, int maxLineLength) {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }
        if (maxLineLength < 1) {
            throw new IllegalArgumentException("maxLineLength must be positive");
        }
        this.reader = new PushbackReader(reader, 1);
        this.maxLineLength = maxLineLength;
    }

    String readLine() throws IOException {
        if (skipLineFeed) {
            skipLineFeed = false;
            int next = reader.read();
            if (next == -1) {
                return null;
            }
            if (next != '\n') {
                reader.unread(next);
            }
        }

        StringBuilder line = new StringBuilder(Math.min(maxLineLength, 256));
        while (true) {
            int value = reader.read();
            if (value == -1) {
                return line.length() == 0 ? null : line.toString();
            }
            if (value == '\n') {
                return line.toString();
            }
            if (value == '\r') {
                skipLineFeed = true;
                return line.toString();
            }
            if (line.length() >= maxLineLength) {
                throw new IOException("SSE line exceeded the size limit");
            }
            line.append((char) value);
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
