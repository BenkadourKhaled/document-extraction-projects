package dz.DocuScan.document.service;

import dz.DocuScan.document.dto.ExtractedFieldView;
import dz.DocuScan.document.dto.FieldCorrectionRequest;
import dz.DocuScan.document.dto.FieldCorrectionView;
import dz.DocuScan.document.dto.FieldSourceView;
import dz.DocuScan.document.field.FieldNormalizer;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.repository.FieldRepository;
import dz.DocuScan.document.exception.DocumentException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model and correction service for traceable fields.
 *
 * <p>Corrections are append-only. The effective value is resolved across the
 * whole document lineage, so a correction remains effective after a
 * re-extraction even though the new extraction keeps its own raw value and
 * source spans.</p>
 */
@Service
public class FieldService {

    private final FieldRepository fields;
    private final ExtractionService extractions;
    private final AuditService auditService;

    public FieldService(
            FieldRepository fields,
            ExtractionService extractions,
            AuditService auditService) {
        this.fields = fields;
        this.extractions = extractions;
        this.auditService = auditService;
    }

    public List<ExtractedFieldView> list(UUID documentId, AuthenticatedUser actor) {
        ExtractionJob job = extractions.get(documentId, actor);
        return fields(job);
    }

    /** Loads fields for an already access-checked job; used by list and exports. */
    public List<ExtractedFieldView> fields(ExtractionJob job) {
        if (job == null || job.getOrganizationId() == null) return List.of();
        if (job.getStatus() != ExtractionStatus.COMPLETED) return List.of();

        List<FieldRepository.FieldRow> rows =
                fields.findByDocument(job.getOrganizationId(), job.getId());
        if (rows.isEmpty()) return List.of();

        Map<UUID, List<FieldRepository.SourceRow>> sources = new LinkedHashMap<>();
        for (FieldRepository.SourceRow source :
                fields.findSourcesByDocument(job.getOrganizationId(), job.getId())) {
            sources.computeIfAbsent(source.fieldId(), key -> new ArrayList<>()).add(source);
        }

        Map<String, List<FieldRepository.CorrectionRow>> corrections = new LinkedHashMap<>();
        for (FieldRepository.CorrectionRow correction :
                fields.findCorrectionsByLineage(job.getOrganizationId(), job.getLineageId())) {
            corrections.computeIfAbsent(correction.fieldKey(), key -> new ArrayList<>()).add(correction);
        }

        List<ExtractedFieldView> views = new ArrayList<>(rows.size());
        for (FieldRepository.FieldRow row : rows) {
            List<FieldRepository.SourceRow> fieldSources =
                    sources.getOrDefault(row.id(), List.of());
            List<FieldRepository.CorrectionRow> history =
                    corrections.getOrDefault(row.fieldKey(), List.of());

            FieldRepository.CorrectionRow current = history.stream()
                    .filter(FieldRepository.CorrectionRow::active)
                    .reduce((first, second) -> second)
                    .orElse(null);

            String effective = current == null ? row.rawValue() : current.correctedValue();
            views.add(new ExtractedFieldView(
                    row.id(),
                    row.documentId(),
                    row.lineageId(),
                    row.fieldKey(),
                    row.label(),
                    row.fieldType(),
                    row.rawValue(),
                    row.normalizedValue(),
                    effective,
                    row.verificationStatus(),
                    row.confidence(),
                    row.extractionMethod(),
                    row.extractorVersion(),
                    current != null,
                    fieldSources.stream().map(this::sourceView).toList(),
                    history.stream().map(this::correctionView).toList()));
        }
        return List.copyOf(views);
    }

    @Transactional
    public ExtractedFieldView correct(
            UUID documentId,
            String fieldKey,
            FieldCorrectionRequest request,
            AuthenticatedUser actor,
            String ipAddress,
            String userAgent) {
        ExtractionJob job = extractions.get(documentId, actor);
        if (job.getStatus() != ExtractionStatus.COMPLETED) {
            throw new DocumentException(409, "Le resultat n'est pas encore disponible.");
        }

        FieldRepository.FieldRow field = fields
                .findField(job.getOrganizationId(), documentId, fieldKey)
                .orElseThrow(() -> new DocumentException(404, "Champ introuvable."));

        String corrected = FieldNormalizer.rawValue(request == null ? null : request.value());
        if (corrected.isBlank()) {
            throw new DocumentException(400, "La valeur corrigée est obligatoire.");
        }
        String reason = request.reason() == null ? null : request.reason().strip();
        if (reason != null && reason.isBlank()) reason = null;

        FieldRepository.CorrectionRow current = fields
                .findActiveCorrection(job.getOrganizationId(), job.getLineageId(), fieldKey)
                .orElse(null);
        String previous = current == null ? field.rawValue() : current.correctedValue();
        Instant now = Instant.now();

        fields.supersedeActiveCorrections(
                job.getOrganizationId(), job.getLineageId(), fieldKey, now);
        fields.insertCorrection(
                job.getOrganizationId(),
                field.id(),
                corrected,
                previous,
                actor == null ? null : actor.id(),
                reason,
                now);

        auditService.record(
                actor,
                "FIELD_CORRECTED",
                documentId,
                ipAddress,
                userAgent,
                Map.of("fieldKey", fieldKey, "corrected", current != null));

        return fields(job).stream()
                .filter(view -> view.fieldKey().equals(fieldKey))
                .findFirst()
                .orElseThrow(() -> new DocumentException(404, "Champ introuvable."));
    }

    private FieldSourceView sourceView(FieldRepository.SourceRow source) {
        return new FieldSourceView(
                source.id(),
                source.pageNumber(),
                source.bboxX0(),
                source.bboxY0(),
                source.bboxX1(),
                source.bboxY1(),
                source.sourceSpace(),
                source.pageWidth(),
                source.pageHeight(),
                source.rotationDeg(),
                source.charStart(),
                source.charEnd(),
                source.sourceText(),
                source.ordinal());
    }

    private FieldCorrectionView correctionView(FieldRepository.CorrectionRow correction) {
        return new FieldCorrectionView(
                correction.id(),
                correction.correctedValue(),
                correction.previousValue(),
                correction.reason(),
                correction.actorDisplayName(),
                correction.createdAt(),
                correction.supersededAt(),
                correction.active());
    }
}
