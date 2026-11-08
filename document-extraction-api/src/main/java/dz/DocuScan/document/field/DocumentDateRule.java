package dz.DocuScan.document.field;

import dz.DocuScan.document.model.FieldType;
import dz.DocuScan.document.model.VerificationStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic document-date detection.
 *
 * <p>The rule never guesses an ambiguous day/month order. A value such as
 * {@code 02/01/2026} is returned as {@link VerificationStatus#AMBIGUOUS} unless
 * one component is greater than 12; a year-first or textual-month date is
 * canonicalized to ISO. Hijri-looking years are also left ambiguous.</p>
 */
public final class DocumentDateRule implements FieldRule {

    public static final String KEY = "document_date";
    public static final String LABEL = "Date du document";
    public static final String EXTRACTOR_VERSION = "rules-date-v1";

    private static final String DIGIT = "[0-9\\u0660-\\u0669\\u06F0-\\u06F9]";

    private static final Pattern PATTERN = Pattern.compile(
            "(?iu)(?:"
                    + "date\\s*(?:d['’]?\\s*[ée]mission|de\\s+facture|facture|du\\s+document|document|of\\s+issue)?"
                    + "|invoice\\s+date"
                    + "|issue\\s+date"
                    + "|تاريخ(?:\\s+(?:الفاتورة|الإصدار|الاصدار))?"
                    + ")\\s*[.:#\\-]?\\s*"
                    + "(?<value>"
                    + DIGIT + "{1,4}\\s*[-/.]\\s*" + DIGIT + "{1,2}\\s*[-/.]\\s*" + DIGIT + "{1,4}"
                    + "|" + DIGIT + "{1,2}(?:er)?\\s+[\\p{L}]+\\s+" + DIGIT + "{4}"
                    + ")");

    private static final Pattern NUMERIC = Pattern.compile(
            "^(\\d{1,4})([-/.])(\\d{1,2})\\2(\\d{1,4})$");
    private static final Pattern TEXT_MONTH = Pattern.compile(
            "^(\\d{1,2})(?:er)?\\s+([\\p{L}]+)\\s+(\\d{4})$");
    private static final Pattern HIJRI = Pattern.compile("(?iu)hijri|هجري|هـ");

    private static final Map<String, Integer> MONTHS = months();

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

        String raw = FieldNormalizer.rawValue(matcher.group("value"));
        if (raw.isBlank()) return List.of();

        ParsedDate parsed = parse(raw, line);
        return List.of(new FieldMatch(
                KEY,
                LABEL,
                FieldType.DATE,
                raw,
                parsed.normalizedValue(),
                parsed.status(),
                matcher.start("value"),
                matcher.end("value")));
    }

    static ParsedDate parse(String raw, String line) {
        String folded = FieldNormalizer.foldDigits(raw)
                .replaceAll("\\s*([-/.])\\s*", "$1")
                .trim();

        boolean hijri = HIJRI.matcher(line == null ? "" : line).find();

        Matcher numeric = NUMERIC.matcher(folded);
        if (numeric.matches()) {
            String first = numeric.group(1);
            String second = numeric.group(3);
            String third = numeric.group(4);

            boolean yearFirst = first.length() == 4;
            int year = Integer.parseInt(yearFirst ? first : third);
            int month = Integer.parseInt(second);
            int day = Integer.parseInt(yearFirst ? third : first);

            if (!valid(year, month, day)) {
                return new ParsedDate(FieldNormalizer.normalizedValue(raw), VerificationStatus.UNVERIFIED);
            }

            boolean ambiguousYear = (yearFirst ? first : third).length() != 4
                    || year < 1900 || year > 2200;
            boolean ambiguousOrder = !yearFirst && day != month && day <= 12 && month <= 12;
            if (hijri || ambiguousYear || ambiguousOrder) {
                return new ParsedDate(FieldNormalizer.normalizedValue(raw), VerificationStatus.AMBIGUOUS);
            }
            return new ParsedDate(LocalDate.of(year, month, day).toString(), VerificationStatus.VERIFIED);
        }

        Matcher textual = TEXT_MONTH.matcher(folded);
        if (textual.matches()) {
            int day = Integer.parseInt(textual.group(1));
            Integer month = monthNumber(textual.group(2));
            int year = Integer.parseInt(textual.group(3));
            if (month == null || !valid(year, month, day) || hijri) {
                return new ParsedDate(FieldNormalizer.normalizedValue(raw), VerificationStatus.UNVERIFIED);
            }
            return new ParsedDate(LocalDate.of(year, month, day).toString(), VerificationStatus.VERIFIED);
        }

        return new ParsedDate(FieldNormalizer.normalizedValue(raw), VerificationStatus.UNVERIFIED);
    }

    private static boolean valid(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Integer monthNumber(String value) {
        String key = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return MONTHS.get(key);
    }

    private static Map<String, Integer> months() {
        Map<String, Integer> months = new HashMap<>();
        String[][] french = {
                {"janvier", "1"}, {"fevrier", "2"}, {"mars", "3"}, {"avril", "4"},
                {"mai", "5"}, {"juin", "6"}, {"juillet", "7"}, {"aout", "8"},
                {"septembre", "9"}, {"octobre", "10"}, {"novembre", "11"}, {"decembre", "12"},
                {"january", "1"}, {"february", "2"}, {"march", "3"}, {"april", "4"},
                {"may", "5"}, {"june", "6"}, {"july", "7"}, {"august", "8"},
                {"september", "9"}, {"october", "10"}, {"november", "11"}, {"december", "12"}
        };
        for (String[] entry : french) {
            months.put(entry[0], Integer.parseInt(entry[1]));
        }
        String[] arabic = {
                "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أوت", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        };
        for (int i = 0; i < arabic.length; i++) {
            months.put(arabic[i], i + 1);
        }
        months.put("ابريل", 4);
        months.put("جوان", 6);
        months.put("جويلية", 7);
        months.put("اوت", 8);
        months.put("اغسطس", 8);
        months.put("اكتوبر", 10);
        return Map.copyOf(months);
    }

    record ParsedDate(String normalizedValue, VerificationStatus status) {
    }
}
