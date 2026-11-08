package dz.DocuScan.document.dto;

import dz.DocuScan.document.model.ExtractionMethod;
import java.util.List;

public record PageResult(
        int number,
        String text,
        ExtractionMethod method,
        Double confidence,
        List<PageSpan> spans) {

    public PageResult {
        text = text == null ? "" : text;
        spans = spans == null ? List.of() : List.copyOf(spans);
    }

    /** Backward-compatible constructor for callers that do not capture spans. */
    public PageResult(int number, String text, ExtractionMethod method, Double confidence) {
        this(number, text, method, confidence, List.of());
    }
}
