package dz.DocuScan.document.dto;

import java.util.UUID;

/** Line-level source region for an extracted field. */
public record FieldSourceView(
        UUID id,
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
