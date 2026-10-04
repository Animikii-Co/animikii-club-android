package club.animikii.radio.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegalDocumentFormatter {
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");

    private LegalDocumentFormatter() { }

    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder plainText = new StringBuilder(normalized.length());
        for (String sourceLine : lines) {
            String line = sourceLine;
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                line = heading.group(1);
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                line = "• " + line.substring(2);
            } else if (line.startsWith("> ")) {
                line = line.substring(2);
            } else if (line.equals(">")) {
                line = "";
            }

            line = replaceLinks(line).replace("`", "").replace("**", "");
            plainText.append(line).append('\n');
        }

        int end = plainText.length();
        while (end > 0 && plainText.charAt(end - 1) == '\n') {
            end--;
        }
        return plainText.substring(0, end);
    }

    private static String replaceLinks(String text) {
        Matcher matcher = LINK.matcher(text);
        StringBuffer formatted = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + " (" + matcher.group(2) + ")";
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(formatted);
        return formatted.toString();
    }
}
