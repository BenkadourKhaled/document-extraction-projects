package dz.DocuScan.document.field;

import dz.DocuScan.document.model.FieldType;
import dz.DocuScan.document.model.VerificationStatus;
import java.util.List;

/** Persistence-neutral field produced by the deterministic rule engine. */
public record FieldDraft(
        String fieldKey,
        String label,
        FieldType fieldType,
        String rawValue,
        String normalizedValue,
        Double confidence,
        String extractionMethod,
        String extractorVersion,
        VerificationStatus verificationStatus,
        List<FieldSourceDraft> sources) {

    public FieldDraft {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /** A line-level source region; coordinates are normalized to [0, 1]. */
    public record FieldSourceDraft(
            int pageNumber,
            double bboxX0,
            double bboxY0,
            double bboxX1,
            double bboxY1,
            String sourceSpace,
            double pageWidth,
            double pageHeight,
            int rotationDeg,
            int charStart,
            int charEnd,
            String sourceText,
            int ordinal) {
    }
}
