package dz.DocuScan.document.extractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrTextNormalizerTest {

    @Test
    void normalizesArabicPresentationForms() {
        assertEquals("لا", OcrTextNormalizer.normalizeLine("\uFEFB"));
        assertEquals("لآ", OcrTextNormalizer.normalizeLine("\uFEF5"));
    }

    @Test
    void removesInvisibleBidirectionalAndControlCharacters() {
        String value = OcrTextNormalizer.normalizeLine("abc\u200F de\u2066f\u0007");
        assertEquals("abc def", value);
    }

    @Test
    void normalizesWhitespace() {
        assertEquals("a b c", OcrTextNormalizer.normalizeLine("  a\t b\u00A0c  "));
    }

    @Test
    void keepsAtMostOneBlankLineBetweenParagraphs() {
        assertEquals("a\n\nb", OcrTextNormalizer.normalize("a\n\n\nb"));
        assertEquals("a\nb", OcrTextNormalizer.normalize("a\nb"));
    }

    @Test
    void rejectsPunctuationOnlyLines() {
        assertFalse(OcrTextNormalizer.isUsefulLine("???"));
        assertFalse(OcrTextNormalizer.isUsefulLine("..."));
        assertFalse(OcrTextNormalizer.isUsefulLine(""));
        assertTrue(OcrTextNormalizer.isUsefulLine("مرحبا"));
        assertTrue(OcrTextNormalizer.isUsefulLine("123"));
    }

    @Test
    void qualitySeparatesReadableTextFromNoise() {
        assertTrue(OcrTextNormalizer.quality("Texte lisible 2026") > 0.8);
        assertEquals(0.0, OcrTextNormalizer.quality("??? !!!"));
        assertTrue(OcrTextNormalizer.usefulCharacters("مرحبا 123") == 8);
    }
}
