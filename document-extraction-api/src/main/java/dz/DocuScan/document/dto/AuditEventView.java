package dz.DocuScan.document.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        UUID actorId,
        String action,
        UUID documentId,
        String ipAddress,
        String userAgent,
        String metadata,
        Instant createdAt) {
}
