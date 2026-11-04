package dz.DocuScan.document.dto;

import dz.DocuScan.document.model.ExtractionStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        String fileName,
        String mimeType,
        ExtractionStatus status,
        int pageCount,
        long sizeBytes,
        String languages,
        long durationMs,
        String error,
        Instant createdAt,
        Instant completedAt) {
}
