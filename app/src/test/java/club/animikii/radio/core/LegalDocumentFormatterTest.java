package club.animikii.radio.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LegalDocumentFormatterTest {
    @Test
    public void formatsSimpleMarkdownForReadableInAppText() {
        String markdown = "# Privacy policy\n\n"
                + "## Listener data\n\n"
                + "- IP address\n"
                + "- `User-agent`\n\n"
                + "See [AzuraCast](https://example.com).\n";

        assertEquals("Privacy policy\n\n"
                        + "Listener data\n\n"
                        + "• IP address\n"
                        + "• User-agent\n\n"
                        + "See AzuraCast (https://example.com).",
                LegalDocumentFormatter.toPlainText(markdown));
    }
}
