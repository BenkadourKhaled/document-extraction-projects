package dz.DocuScan.document.field;

import dz.DocuScan.document.model.FieldType;
import dz.DocuScan.document.model.VerificationStatus;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic invoice/document reference detection.
 *
 * <p>A value is only produced when a recognizable reference label is present on
 * the same line and the candidate contains at least one digit. This avoids
 * inventing a field from a random number in the page.</p>
 */
public final class ReferenceNumberRule implements FieldRule {

    public static final String KEY = "reference_number";
    public static final String LABEL = "Numéro de référence";
    public static final String EXTRACTOR_VERSION = "rules-reference-v1";

    private static final Pattern PATTERN = Pattern.compile(
            "(?iu)(?:"
                    + "n[°ºo]\\s*(?:de\\s+)?facture"
                    + "|facture\\s*(?:n[°ºo]|no|num(?:[ée]ro)?)"
                    + "|num(?:[ée]ro)?\\s+de\\s+facture"
                    + "|invoice\\s*(?:no|number|n[°ºo])"
                    + "|document\\s*(?:no|number)"
                    + "|r[ée]f(?:[ée]rence)?"
                    + "|reference"
                    + "|رقم\\s+(?:الفاتورة|المستند|الوثيقة)"
                    + "|(?:مرجع|المرجع)"
                    + "|فاتورة\\s+رقم"
                    + "|(?:no|n[°ºo])"
                    + ")\\s*[.:#\\-]?\\s*"
                    + "(?<value>(?=[\\p{L}\\p{N}._/\\-]{0,60}[0-9\\u0660-\\u0669\\u06F0-\\u06F9])"
                    + "[\\p{L}\\p{N}][\\p{L}\\p{N}._/\\-]{1,60})");

    @Override
    public String fieldKey() {
        return KEY;
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public String extractorVersion() {
        return EXTRACTOR_VERSION;
    }

    @Override
    public List<FieldMatch> matches(String line) {
        if (line == null || line.isBlank()) return List.of();

        Matcher matcher = PATTERN.matcher(line);
        if (!matcher.find()) return List.of();

        String detected = matcher.group("value");
        String cleaned = FieldNormalizer.trimTrailingPunctuation(detected);
        if (cleaned.isBlank() || !FieldNormalizer.containsDigit(cleaned)) {
            return List.of();
        }

        int removed = detected.length() - cleaned.length();
        int start = matcher.start("value");
        int end = matcher.end("value") - Math.max(0, removed);

        return List.of(new FieldMatch(
                KEY,
                LABEL,
                FieldType.IDENTIFIER,
                FieldNormalizer.rawValue(cleaned),
                FieldNormalizer.normalizedValue(cleaned),
                VerificationStatus.VERIFIED,
                start,
                end));
    }
}
