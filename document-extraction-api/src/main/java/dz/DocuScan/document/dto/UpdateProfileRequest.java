package dz.DocuScan.document.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Email @Size(max = 320) String email) {
}
