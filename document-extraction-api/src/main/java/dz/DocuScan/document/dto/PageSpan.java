package dz.DocuScan.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A traceable line of text in a page. Coordinates are normalized to [0, 1]
 * with a top-left origin on the page as displayed after rotation.
 *
 * <p>Instances are persisted inside {@code documents.pages_json} and are
 * therefore kept as a small, serializable value object. Offsets are relative
 * to the page text stored in the same {@link PageResult}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageSpan(
        String text,
        int charStart,
        int charEnd,
        double bboxX0,
        double bboxY0,
        double bboxX1,
        double bboxY1,
        String sourceSpace,
        double pageWidth,
        double pageHeight,
        int rotationDeg,
        Double confidence) {

    public PageSpan {
        text = text == null ? "" : text;
        sourceSpace = sourceSpace == null ? "UNKNOWN" : sourceSpace;
    }
}
