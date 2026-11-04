package dz.DocuScan.document.model;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        UUID organizationId,
        String organizationName,
        String email,
        String displayName,
        Role role) {
}
