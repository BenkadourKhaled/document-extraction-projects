package dz.DocuScan.document.field;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normalization helpers for traceable field values.
 *
 * <p>Three representations are kept deliberately separate:</p>
 * <ul>
 *   <li>the raw value is the text exactly as detected, with only invisible
 *       bidi/control characters removed;</li>
 *   <li>the normalized value is a canonical comparison form (NFC, trimmed,
 *       Arabic-Indic/Persian digits folded to ASCII);</li>
 *   <li>the effective value is the raw value unless a reviewer correction
 *       supersedes it.</li>
 * </ul>
 *
 * <p>Digit folding is only used for comparison and search. It is never used to
 * render or export a field value, which preserves Arabic-Indic/Persian digits
 * and the logical character order of mixed-direction text.</p>
 */
public final class FieldNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u061C\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private FieldNormalizer() {
    }

    /** Removes only invisible formatting/control characters, never visible text. */
    public static String removeInvisible(String value) {
        if (value == null) return "";
        return INVISIBLE.matcher(value).replaceAll("");
    }

    /** Exact value kept for display and export. */
    public static String rawValue(String value) {
        if (value == null) return "";
        return removeInvisible(value).strip();
    }

    /** NFC + trimmed value with internal horizontal whitespace collapsed. */
    public static String nfc(String value) {
        if (value == null) return "";
        String cleaned = removeInvisible(value).strip();
        return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
                .replaceAll(HORIZONTAL_WHITESPACE.pattern(), " ");
    }

    /** Folds Arabic-Indic and Persian digits to ASCII, preserving every other character. */
    public static String foldDigits(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            int digit = Character.getNumericValue(codePoint);
            if (codePoint >= 0x0660 && codePoint <= 0x0669) {
                out.append((char) ('0' + digit));
            } else if (codePoint >= 0x06F0 && codePoint <= 0x06F9) {
                out.append((char) ('0' + digit));
            } else {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString();
    }

    /** Canonical comparison form: NFC, folded digits, collapsed whitespace. */
    public static String normalizedValue(String value) {
        return foldDigits(nfc(value));
    }

    /** Stable search key; lower-cased comparison form. */
    public static String comparisonValue(String value) {
        return WHITESPACE.matcher(normalizedValue(value)).replaceAll(" ").trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    /** True when the value contains at least one ASCII/Arabic-Indic/Persian digit. */
    public static boolean containsDigit(String value) {
        if (value == null) return false;
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= '0' && codePoint <= '9')
                        || (codePoint >= 0x0660 && codePoint <= 0x0669)
                        || (codePoint >= 0x06F0 && codePoint <= 0x06F9));
    }

    /** Removes trailing separators that regexes commonly capture by accident. */
    public static String trimTrailingPunctuation(String value) {
        if (value == null) return "";
        String result = value.strip();
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':') {
                result = result.substring(0, result.length() - 1).stripTrailing();
            } else {
                break;
            }
        }
        return result;
    }
}
