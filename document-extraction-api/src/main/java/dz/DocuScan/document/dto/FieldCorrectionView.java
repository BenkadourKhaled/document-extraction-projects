package dz.DocuScan.document.dto;

import java.time.Instant;
import java.util.UUID;

/** Append-only correction history entry. */
public record FieldCorrectionView(
        UUID id,
        String correctedValue,
        String previousValue,
        String reason,
        String actorDisplayName,
        Instant createdAt,
        Instant supersededAt,
        boolean active) {
}
