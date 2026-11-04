package dz.DocuScan.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReprocessRequest(
        @NotEmpty List<@NotBlank String> languages) {
}
