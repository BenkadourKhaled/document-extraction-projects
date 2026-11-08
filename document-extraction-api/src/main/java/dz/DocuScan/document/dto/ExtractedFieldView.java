package dz.DocuScan.document.dto;

import java.util.List;
import java.util.UUID;

/**
 * Review view of one extracted field.
 *
 * <p>{@code rawValue} is immutable and preserved exactly. {@code effectiveValue}
 * is the latest current correction in the document lineage, otherwise the raw
 * value. {@code normalizedValue} is only a comparison form and is never used to
 * replace the displayed/exported raw value.</p>
 */
public record ExtractedFieldView(
        UUID id,
        UUID documentId,
        UUID lineageId,
        String fieldKey,
        String label,
        String fieldType,
        String rawValue,
        String normalizedValue,
        String effectiveValue,
        String verificationStatus,
        Double confidence,
        String extractionMethod,
        String extractorVersion,
        boolean corrected,
        List<FieldSourceView> sources,
        List<FieldCorrectionView> corrections) {

    public ExtractedFieldView {
        sources = sources == null ? List.of() : List.copyOf(sources);
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
    }
}
