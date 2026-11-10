package dz.DocuScan.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** New correction appended to a field's lineage history. */
public record FieldCorrectionRequest(
        @NotBlank(message = "La valeur corrigée est obligatoire.")
        @Size(max = 5000, message = "La valeur corrigée est trop longue.")
        String value,

        @Size(max = 500, message = "Le motif est trop long.")
        String reason) {
}
