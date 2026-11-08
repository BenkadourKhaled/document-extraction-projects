package dz.DocuScan.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dz.DocuScan.document.dto.ExtractedFieldView;
import dz.DocuScan.document.dto.FieldCorrectionView;
import dz.DocuScan.document.dto.FieldSourceView;
import dz.DocuScan.document.model.ExtractionJob;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for the TXT and JSON export formats.
 *
 * <p>Both controller export paths (one document and ZIP) call this service so
 * the field section can never drift between them. JSON is additive: every
 * existing key is kept, then {@code schema_version} and {@code fields} are added.
 * TXT keeps the full extracted text first and appends a clearly delimited field
 * section. Both formats are UTF-8 without BOM and preserve raw values exactly.</p>
 */
@Service
public class ExtractionExportService {

    public static final String SCHEMA_VERSION = "field-traceability-v1";
    public static final String TXT_FIELDS_HEADER = "--- Extracted fields (effective values) ---";

    private final ExtractionService extractions;
    private final FieldService fields;
    private final ObjectMapper mapper;

    public ExtractionExportService(
            ExtractionService extractions,
            FieldService fields,
            ObjectMapper mapper) {
        this.extractions = extractions;
        this.fields = fields;
        this.mapper = mapper;
    }

    public byte[] txtBytes(ExtractionJob job) {
        return txt(job).getBytes(StandardCharsets.UTF_8);
    }

    public String txt(ExtractionJob job) {
        String fullText = job.getFullText() == null ? "" : job.getFullText();
        List<ExtractedFieldView> views = fields.fields(job);
        if (views.isEmpty()) return fullText;

        StringBuilder out = new StringBuilder(fullText);
        if (!fullText.isEmpty() && !fullText.endsWith("\n")) out.append('\n');
        out.append('\n').append(TXT_FIELDS_HEADER).append('\n');
        for (ExtractedFieldView field : views) {
            out.append(field.label())
                    .append(" (")
                    .append(field.fieldKey())
                    .append("): ")
                    .append(field.effectiveValue())
                    .append(" [")
                    .append(field.verificationStatus())
                    .append(field.corrected() ? ", CORRECTED" : "")
                    .append("]\n");
        }
        return out.toString();
    }

    public byte[] jsonBytes(ExtractionJob job) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json(job));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de generer le JSON.", ex);
        }
    }

    public ObjectNode json(ExtractionJob job) {
        ObjectNode root = mapper.valueToTree(extractions.response(job));
        root.put("schema_version", SCHEMA_VERSION);

        ArrayNode fieldsNode = root.putArray("fields");
        for (ExtractedFieldView field : fields.fields(job)) {
            ObjectNode node = fieldsNode.addObject();
            node.put("id", field.id().toString());
            node.put("document_id", field.documentId().toString());
            node.put("lineage_id", field.lineageId().toString());
            node.put("field_key", field.fieldKey());
            node.put("label", field.label());
            node.put("field_type", field.fieldType());
            node.put("raw_value", field.rawValue());
            node.put("normalized_value", field.normalizedValue());
            node.put("effective_value", field.effectiveValue());
            node.put("verification_status", field.verificationStatus());
            if (field.confidence() == null) {
                node.putNull("confidence");
            } else {
                node.put("confidence", field.confidence());
            }
            node.put("extraction_method", field.extractionMethod());
            node.put("extractor_version", field.extractorVersion());
            node.put("corrected", field.corrected());

            ArrayNode sourcesNode = node.putArray("sources");
            for (FieldSourceView source : field.sources()) {
                ObjectNode sourceNode = sourcesNode.addObject();
                sourceNode.put("page_number", source.pageNumber());
                sourceNode.put("bbox_x0", source.bboxX0());
                sourceNode.put("bbox_y0", source.bboxY0());
                sourceNode.put("bbox_x1", source.bboxX1());
                sourceNode.put("bbox_y1", source.bboxY1());
                sourceNode.put("source_space", source.sourceSpace());
                sourceNode.put("page_width", source.pageWidth());
                sourceNode.put("page_height", source.pageHeight());
                sourceNode.put("rotation_deg", source.rotationDeg());
                sourceNode.put("char_start", source.charStart());
                sourceNode.put("char_end", source.charEnd());
                sourceNode.put("source_text", source.sourceText());
                sourceNode.put("ordinal", source.ordinal());
            }

            ArrayNode correctionsNode = node.putArray("corrections");
            for (FieldCorrectionView correction : field.corrections()) {
                ObjectNode correctionNode = correctionsNode.addObject();
                correctionNode.put("id", correction.id().toString());
                correctionNode.put("corrected_value", correction.correctedValue());
                correctionNode.put("previous_value", correction.previousValue());
                correctionNode.put("reason", correction.reason());
                correctionNode.put("actor_display_name", correction.actorDisplayName());
                correctionNode.put("created_at", correction.createdAt().toString());
                if (correction.supersededAt() == null) {
                    correctionNode.putNull("superseded_at");
                } else {
                    correctionNode.put("superseded_at", correction.supersededAt().toString());
                }
                correctionNode.put("active", correction.active());
            }
        }
        return root;
    }
}
