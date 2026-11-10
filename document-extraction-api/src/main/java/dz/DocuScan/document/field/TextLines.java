package dz.DocuScan.document.field;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits page text into trimmed lines while keeping page-level offsets.
 *
 * <p>Both the deterministic rules and the optional Ollama extractor need the
 * same line boundaries: a value can only be trusted when it is found on a line
 * that carries a recognizable label, and source offsets must stay relative to
 * the page text stored in {@code pages_json}.</p>
 */
final class TextLines {

    private TextLines() {
    }

    static List<Line> of(String text) {
        List<Line> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        int start = 0;
        while (start <= text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            int cleanStart = start;
            int cleanEnd = end;
            while (cleanStart < cleanEnd && (text.charAt(cleanStart) == '\r'
                    || Character.isWhitespace(text.charAt(cleanStart)))) {
                cleanStart++;
            }
            while (cleanEnd > cleanStart && (text.charAt(cleanEnd - 1) == '\r'
                    || Character.isWhitespace(text.charAt(cleanEnd - 1)))) {
                cleanEnd--;
            }
            lines.add(new Line(cleanStart, cleanEnd, text.substring(cleanStart, cleanEnd)));
            if (newline < 0) break;
            start = newline + 1;
        }
        return lines;
    }

    record Line(int start, int end, String text) {
    }
}
