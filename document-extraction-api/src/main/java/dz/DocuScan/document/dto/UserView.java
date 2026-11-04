package dz.DocuScan.document.dto;

import dz.DocuScan.document.model.Role;
import java.util.UUID;

public record UserView(
        UUID id,
        UUID organizationId,
        String organizationName,
        String email,
        String displayName,
        Role role) {
}
