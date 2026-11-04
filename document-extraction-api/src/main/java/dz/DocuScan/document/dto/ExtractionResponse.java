package dz.DocuScan.document.dto;

import dz.DocuScan.document.model.ExtractionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExtractionResponse(
        UUID id,
        ExtractionStatus status,
        String fileName,
        String mimeType,
        int pageCount,
        String fullText,
        List<PageResult> pages,
        long durationMs,
        String error,
        Instant createdAt,
        String languages) {
}
