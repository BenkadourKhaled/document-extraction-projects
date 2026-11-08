package dz.DocuScan.document.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldNormalizerTest {

    @Test
    void rawValueRemovesInvisibleControlsButKeepsArabicIndicDigits() {
        String raw = FieldNormalizer.rawValue("\u200Fمرجع-١٢٣\u200E");

        assertEquals("مرجع-١٢٣", raw);
    }

    @Test
    void normalizedValueFoldsArabicIndicAndPersianDigits() {
        assertEquals("1234567890", FieldNormalizer.foldDigits(
                "\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u0660"));
        assertEquals("1234567890", FieldNormalizer.foldDigits(
                "\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9\u06F0"));
        assertEquals("مرجع-123", FieldNormalizer.normalizedValue(
                "مرجع-\u0661\u0662\u0663"));
    }

    @Test
    void normalizedValueDoesNotReplaceTheRawValue() {
        String raw = FieldNormalizer.rawValue("FACT-٢٠٢٦");
        String normalized = FieldNormalizer.normalizedValue(raw);

        assertEquals("FACT-٢٠٢٦", raw);
        assertEquals("FACT-2026", normalized);
    }

    @Test
    void comparisonValueIsLowerCasedAndWhitespaceStable() {
        assertEquals("facture 2026", FieldNormalizer.comparisonValue("  Facture\t٢٠٢٦ "));
    }

    @Test
    void containsDigitRecognizesAllSupportedDigitSets() {
        assertTrue(FieldNormalizer.containsDigit("ABC-١"));
        assertTrue(FieldNormalizer.containsDigit("ABC-۱"));
        assertTrue(FieldNormalizer.containsDigit("ABC-1"));
        assertFalse(FieldNormalizer.containsDigit("ABC"));
    }

    @Test
    void trimsOnlyTrailingPunctuation() {
        assertEquals("2026-001", FieldNormalizer.trimTrailingPunctuation("2026-001.,"));
        assertEquals("Facture (2026)", FieldNormalizer.trimTrailingPunctuation("Facture (2026)"));
    }
}
