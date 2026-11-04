package dz.DocuScan.document.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ZipDownloadRequest(
        @NotEmpty List<UUID> ids,
        String format) {
}
