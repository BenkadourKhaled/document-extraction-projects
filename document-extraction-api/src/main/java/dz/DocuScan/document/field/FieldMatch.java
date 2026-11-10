package dz.DocuScan.document.field;

import dz.DocuScan.document.model.FieldType;
import dz.DocuScan.document.model.VerificationStatus;

/**
 * A detected field value inside one line of a page.
 *
 * <p>Offsets are relative to the line text. The extraction service translates
 * them to page-level offsets before looking up the source span.</p>
 */
public record FieldMatch(
        String fieldKey,
        String label,
        FieldType fieldType,
        String rawValue,
        String normalizedValue,
        VerificationStatus verificationStatus,
        int matchStart,
        int matchEnd) {
}
