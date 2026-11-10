package dz.DocuScan.document.repository;

import dz.DocuScan.document.field.FieldDraft;
import dz.DocuScan.document.model.ExtractionJob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FieldRepository {

    public record FieldRow(
            UUID id,
            UUID organizationId,
            UUID documentId,
            UUID lineageId,
            String fieldKey,
            String fieldType,
            String label,
            String rawValue,
            String normalizedValue,
            Double confidence,
            String extractionMethod,
            String extractorVersion,
            String verificationStatus,
            Instant createdAt) {
    }

    public record SourceRow(
            UUID id,
            UUID fieldId,
            int pageNumber,
            double bboxX0,
            double bboxY0,
            double bboxX1,
            double bboxY1,
            String sourceSpace,
            double pageWidth,
            double pageHeight,
            int rotationDeg,
            int charStart,
            int charEnd,
            String sourceText,
            int ordinal) {
    }

    public record CorrectionRow(
            UUID id,
            UUID fieldId,
            String fieldKey,
            String correctedValue,
            String previousValue,
            String reason,
            String actorDisplayName,
            Instant createdAt,
            Instant supersededAt,
            boolean active) {
    }

    private final JdbcTemplate jdbc;

    public FieldRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsForDocument(UUID organizationId, UUID documentId) {
        Boolean exists = jdbc.queryForObject("""
                        SELECT EXISTS(
                            SELECT 1 FROM extracted_fields
                            WHERE organization_id = ? AND document_id = ?
                        )
                        """,
                Boolean.class, organizationId, documentId);
        return Boolean.TRUE.equals(exists);
    }

    @Transactional
    public void insertAll(ExtractionJob job, List<FieldDraft> drafts) {
        for (FieldDraft draft : drafts) {
            UUID fieldId = UUID.randomUUID();
            jdbc.update("""
                            INSERT INTO extracted_fields (
                                id, organization_id, document_id, lineage_id, field_key,
                                field_type, label, raw_value, normalized_value, confidence,
                                extraction_method, extractor_version, verification_status, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    fieldId,
                    job.getOrganizationId(),
                    job.getId(),
                    job.getLineageId() == null ? job.getId() : job.getLineageId(),
                    draft.fieldKey(),
                    draft.fieldType().name(),
                    draft.label(),
                    draft.rawValue(),
                    draft.normalizedValue(),
                    draft.confidence(),
                    draft.extractionMethod(),
                    draft.extractorVersion(),
                    draft.verificationStatus().name(),
                    Timestamp.from(Instant.now()));

            int ordinal = 0;
            for (FieldDraft.FieldSourceDraft source : draft.sources()) {
                jdbc.update("""
                                INSERT INTO field_sources (
                                    id, organization_id, field_id, page_number,
                                    bbox_x0, bbox_y0, bbox_x1, bbox_y1, source_space,
                                    page_width, page_height, rotation_deg,
                                    char_start, char_end, source_text, ordinal
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        UUID.randomUUID(),
                        job.getOrganizationId(),
                        fieldId,
                        source.pageNumber(),
                        source.bboxX0(),
                        source.bboxY0(),
                        source.bboxX1(),
                        source.bboxY1(),
                        source.sourceSpace() == null ? "UNKNOWN" : source.sourceSpace(),
                        source.pageWidth(),
                        source.pageHeight(),
                        source.rotationDeg(),
                        source.charStart(),
                        source.charEnd(),
                        source.sourceText(),
                        ordinal++);
            }
        }
    }

    public List<FieldRow> findByDocument(UUID organizationId, UUID documentId) {
        return jdbc.query("""
                        SELECT id, organization_id, document_id, lineage_id, field_key,
                               field_type, label, raw_value, normalized_value, confidence,
                               extraction_method, extractor_version, verification_status, created_at
                        FROM extracted_fields
                        WHERE organization_id = ? AND document_id = ?
                        ORDER BY field_key
                        """,
                (rs, rowNum) -> mapField(rs),
                organizationId, documentId);
    }

    public Optional<FieldRow> findField(UUID organizationId, UUID documentId, String fieldKey) {
        return jdbc.query("""
                        SELECT id, organization_id, document_id, lineage_id, field_key,
                               field_type, label, raw_value, normalized_value, confidence,
                               extraction_method, extractor_version, verification_status, created_at
                        FROM extracted_fields
                        WHERE organization_id = ? AND document_id = ? AND field_key = ?
                        """,
                (rs, rowNum) -> mapField(rs),
                organizationId, documentId, fieldKey)
                .stream()
                .findFirst();
    }

    public List<SourceRow> findSourcesByDocument(UUID organizationId, UUID documentId) {
        return jdbc.query("""
                        SELECT fs.id, fs.field_id, fs.page_number, fs.bbox_x0, fs.bbox_y0,
                               fs.bbox_x1, fs.bbox_y1, fs.source_space, fs.page_width,
                               fs.page_height, fs.rotation_deg, fs.char_start, fs.char_end,
                               fs.source_text, fs.ordinal
                        FROM field_sources fs
                        JOIN extracted_fields ef ON ef.id = fs.field_id
                        WHERE fs.organization_id = ? AND ef.organization_id = ?
                          AND ef.document_id = ?
                        ORDER BY fs.field_id, fs.ordinal
                        """,
                (rs, rowNum) -> new SourceRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("field_id", UUID.class),
                        rs.getInt("page_number"),
                        rs.getDouble("bbox_x0"),
                        rs.getDouble("bbox_y0"),
                        rs.getDouble("bbox_x1"),
                        rs.getDouble("bbox_y1"),
                        rs.getString("source_space"),
                        rs.getDouble("page_width"),
                        rs.getDouble("page_height"),
                        rs.getInt("rotation_deg"),
                        rs.getInt("char_start"),
                        rs.getInt("char_end"),
                        rs.getString("source_text"),
                        rs.getInt("ordinal")),
                organizationId, organizationId, documentId);
    }

    public List<CorrectionRow> findCorrectionsByLineage(UUID organizationId, UUID lineageId) {
        return jdbc.query("""
                        SELECT fc.id, fc.field_id, ef.field_key, fc.corrected_value,
                               fc.previous_value, fc.reason, fc.created_at, fc.superseded_at,
                               fc.active_marker, u.display_name AS actor_display_name
                        FROM field_corrections fc
                        JOIN extracted_fields ef ON ef.id = fc.field_id
                        LEFT JOIN users u ON u.id = fc.actor_id
                        WHERE fc.organization_id = ? AND ef.organization_id = ?
                          AND ef.lineage_id = ?
                        ORDER BY fc.created_at, fc.id
                        """,
                (rs, rowNum) -> mapCorrection(rs),
                organizationId, organizationId, lineageId);
    }

    public Optional<CorrectionRow> findActiveCorrection(
            UUID organizationId,
            UUID lineageId,
            String fieldKey) {
        return jdbc.query("""
                        SELECT fc.id, fc.field_id, ef.field_key, fc.corrected_value,
                               fc.previous_value, fc.reason, fc.created_at, fc.superseded_at,
                               fc.active_marker, u.display_name AS actor_display_name
                        FROM field_corrections fc
                        JOIN extracted_fields ef ON ef.id = fc.field_id
                        LEFT JOIN users u ON u.id = fc.actor_id
                        WHERE fc.organization_id = ? AND ef.organization_id = ?
                          AND ef.lineage_id = ? AND ef.field_key = ?
                          AND fc.active_marker = 1
                        ORDER BY fc.created_at DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapCorrection(rs),
                organizationId, organizationId, lineageId, fieldKey)
                .stream()
                .findFirst();
    }

    public int supersedeActiveCorrections(
            UUID organizationId,
            UUID lineageId,
            String fieldKey,
            Instant supersededAt) {
        return jdbc.update("""
                        UPDATE field_corrections
                        SET superseded_at = ?, active_marker = NULL
                        WHERE organization_id = ? AND active_marker = 1
                          AND field_id IN (
                              SELECT id FROM extracted_fields
                              WHERE organization_id = ? AND lineage_id = ? AND field_key = ?
                          )
                        """,
                Timestamp.from(supersededAt),
                organizationId,
                organizationId,
                lineageId,
                fieldKey);
    }

    public void insertCorrection(
            UUID organizationId,
            UUID fieldId,
            String correctedValue,
            String previousValue,
            UUID actorId,
            String reason,
            Instant createdAt) {
        jdbc.update("""
                        INSERT INTO field_corrections (
                            id, organization_id, field_id, corrected_value, previous_value,
                            actor_id, reason, created_at, superseded_at, active_marker
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1)
                        """,
                UUID.randomUUID(),
                organizationId,
                fieldId,
                correctedValue,
                previousValue,
                actorId,
                reason,
                Timestamp.from(createdAt));
    }

    private FieldRow mapField(ResultSet rs) throws SQLException {
        return new FieldRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("lineage_id", UUID.class),
                rs.getString("field_key"),
                rs.getString("field_type"),
                rs.getString("label"),
                rs.getString("raw_value"),
                rs.getString("normalized_value"),
                rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                rs.getString("extraction_method"),
                rs.getString("extractor_version"),
                rs.getString("verification_status"),
                instant(rs, "created_at"));
    }

    private CorrectionRow mapCorrection(ResultSet rs) throws SQLException {
        return new CorrectionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("field_id", UUID.class),
                rs.getString("field_key"),
                rs.getString("corrected_value"),
                rs.getString("previous_value"),
                rs.getString("reason"),
                rs.getString("actor_display_name"),
                instant(rs, "created_at"),
                instant(rs, "superseded_at"),
                rs.getObject("active_marker") != null);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
