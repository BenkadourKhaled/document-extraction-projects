package dz.DocuScan.document.dto;

import java.util.List;

public record BatchExtractionResponse(List<ExtractionResponse> items) {
}
