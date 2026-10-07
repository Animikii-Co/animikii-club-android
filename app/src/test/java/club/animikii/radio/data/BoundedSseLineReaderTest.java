package club.animikii.radio.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.junit.Test;

public class BoundedSseLineReaderTest {
    @Test
    public void handlesCrLfLfAndCrLineEndings() throws IOException {
        try (BoundedSseLineReader reader = new BoundedSseLineReader(
                new StringReader("first\rsecond\r\nthird\nfourth"), 16)) {
            assertEquals("first", reader.readLine());
            assertEquals("second", reader.readLine());
            assertEquals("third", reader.readLine());
            assertEquals("fourth", reader.readLine());
            assertNull(reader.readLine());
        }
    }

    @Test
    public void returnsCrTerminatedLineWithoutReadingAhead() throws IOException {
        Reader input = new Reader() {
            private int position;

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                if (position == 0) {
                    buffer[offset] = 'x';
                    position++;
                    return 1;
                }
                if (position == 1) {
                    buffer[offset] = '\r';
                    position++;
                    return 1;
                }
                throw new IOException("unexpected read past CR terminator");
            }

            @Override
            public void close() { }
        };

        try (BoundedSseLineReader reader = new BoundedSseLineReader(input, 8)) {
            assertEquals("x", reader.readLine());
        }
    }

    @Test
    public void rejectsAnUnterminatedLineBeforeItCanGrowUnbounded() throws IOException {
        try (BoundedSseLineReader reader = new BoundedSseLineReader(
                new StringReader("12345"), 4)) {
            try {
                reader.readLine();
                fail("Expected an overlong SSE line to be rejected");
            } catch (IOException expected) {
                assertEquals("SSE line exceeded the size limit", expected.getMessage());
            }
        }
    }
}
