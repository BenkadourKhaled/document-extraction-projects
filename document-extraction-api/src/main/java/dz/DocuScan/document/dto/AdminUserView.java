package dz.DocuScan.document.dto;

import dz.DocuScan.document.model.Role;
import java.time.Instant;
import java.util.UUID;

public record AdminUserView(
        UUID id,
        String email,
        String displayName,
        Role role,
        boolean enabled,
        Instant createdAt) {
}
