package dz.DocuScan.document.field;

import dz.DocuScan.document.model.VerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceNumberRuleTest {

    private final ReferenceNumberRule rule = new ReferenceNumberRule();

    @Test
    void detectsInvoiceNumberAfterFrenchLabel() {
        String line = "Facture numero 2026-001";
        List<FieldMatch> matches = rule.matches(line);

        assertEquals(1, matches.size());
        FieldMatch match = matches.get(0);
        assertEquals(ReferenceNumberRule.KEY, match.fieldKey());
        assertEquals("2026-001", match.rawValue());
        assertEquals("2026-001", match.normalizedValue());
        assertEquals(VerificationStatus.VERIFIED, match.verificationStatus());
        assertEquals(line.indexOf("2026-001"), match.matchStart());
        assertEquals(line.length(), match.matchEnd());
    }

    @Test
    void detectsReferenceWithDotAfterLabel() {
        List<FieldMatch> matches = rule.matches("Réf. 2026-ABC");

        assertEquals(1, matches.size());
        assertEquals("2026-ABC", matches.get(0).rawValue());
    }

    @Test
    void preservesArabicDigitsInRawAndFoldsThemInNormalized() {
        List<FieldMatch> matches = rule.matches("رقم الفاتورة: FACT-٢٠٢٦");

        assertEquals(1, matches.size());
        assertEquals("FACT-٢٠٢٦", matches.get(0).rawValue());
        assertEquals("FACT-2026", matches.get(0).normalizedValue());
    }

    @Test
    void handlesArabicReferenceLabel() {
        List<FieldMatch> matches = rule.matches("مرجع: ٢٠٢٦/٠٠١");

        assertEquals(1, matches.size());
        assertEquals("٢٠٢٦/٠٠١", matches.get(0).rawValue());
        assertEquals("2026/001", matches.get(0).normalizedValue());
    }

    @Test
    void ignoresLabelsWithoutADigit() {
        assertTrue(rule.matches("Facture numero: aucune").isEmpty());
        assertTrue(rule.matches("Total 2026").isEmpty());
    }

    @Test
    void doesNotInventAReferenceFromABareNumber() {
        assertTrue(rule.matches("2026-001").isEmpty());
        assertFalse(rule.matches("Total: 12 500,00 DZD").size() > 0);
    }
}
