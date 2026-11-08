package dz.DocuScan.document.field;

import dz.DocuScan.document.model.VerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentDateRuleTest {

    private final DocumentDateRule rule = new DocumentDateRule();

    @Test
    void normalizesUnambiguousDayFirstDate() {
        List<FieldMatch> matches = rule.matches("Date: 31/01/2026");

        assertEquals(1, matches.size());
        assertEquals("31/01/2026", matches.get(0).rawValue());
        assertEquals("2026-01-31", matches.get(0).normalizedValue());
        assertEquals(VerificationStatus.VERIFIED, matches.get(0).verificationStatus());
    }

    @Test
    void leavesAmbiguousDayMonthOrderUnresolved() {
        List<FieldMatch> matches = rule.matches("Date: 02/01/2026");

        assertEquals(1, matches.size());
        assertEquals("02/01/2026", matches.get(0).normalizedValue());
        assertEquals(VerificationStatus.AMBIGUOUS, matches.get(0).verificationStatus());
    }

    @Test
    void acceptsYearFirstDate() {
        List<FieldMatch> matches = rule.matches("Date: 2026-01-31");

        assertEquals(1, matches.size());
        assertEquals("2026-01-31", matches.get(0).normalizedValue());
        assertEquals(VerificationStatus.VERIFIED, matches.get(0).verificationStatus());
    }

    @Test
    void normalizesArabicIndicDigitsAndKeepsRawDate() {
        List<FieldMatch> matches = rule.matches("تاريخ الفاتورة: ٣١/٠١/٢٠٢٦");

        assertEquals(1, matches.size());
        assertEquals("٣١/٠١/٢٠٢٦", matches.get(0).rawValue());
        assertEquals("2026-01-31", matches.get(0).normalizedValue());
    }

    @Test
    void acceptsFrenchAndEnglishMonthNames() {
        assertEquals("2026-01-12",
                rule.matches("Date: 12 janvier 2026").get(0).normalizedValue());
        assertEquals("2026-02-12",
                rule.matches("Date: 12 février 2026").get(0).normalizedValue());
        assertEquals("2026-03-12",
                rule.matches("Invoice date: 12 March 2026").get(0).normalizedValue());
    }

    @Test
    void marksInvalidDatesUnverified() {
        List<FieldMatch> matches = rule.matches("Date: 31/02/2026");

        assertEquals(1, matches.size());
        assertEquals(VerificationStatus.UNVERIFIED, matches.get(0).verificationStatus());
    }

    @Test
    void marksHijriLookingDatesAmbiguous() {
        List<FieldMatch> matches = rule.matches("Date: 12/07/1447 هجري");

        assertEquals(1, matches.size());
        assertEquals(VerificationStatus.AMBIGUOUS, matches.get(0).verificationStatus());
    }

    @Test
    void ignoresBareDatesWithoutALabel() {
        assertTrue(rule.matches("Total: 2026-01-31").isEmpty());
    }
}
