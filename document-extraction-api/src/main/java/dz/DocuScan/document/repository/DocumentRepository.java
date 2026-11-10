package dz.DocuScan.document.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.dto.DocumentSummary;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    public record ExpiredDocument(
            UUID id,
            UUID organizationId,
            UUID ownerId,
            String storageKey,
            String fileName) {
    }

    public record StorageKeyRecord(UUID id, String storageKey) {
    }

    private static final String DOCUMENT_COLUMNS = """
            id, organization_id, owner_id, file_name, mime_type, size_bytes, sha256,
            storage_key, languages, status, page_count, full_text, pages_json,
            duration_ms, error, created_at, updated_at, completed_at, retention_until,
            lineage_id, source_document_id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DocumentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insert(ExtractionJob job) {
        jdbc.update("""
                        INSERT INTO documents (
                            id, organization_id, owner_id, file_name, mime_type, size_bytes, sha256,
                            storage_key, languages, status, page_count, full_text, pages_json,
                            duration_ms, error, created_at, updated_at, completed_at, retention_until,
                            lineage_id, source_document_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                job.getId(),
                job.getOrganizationId(),
                job.getOwnerId(),
                job.getFileName(),
                job.getMimeType(),
                job.getSizeBytes(),
                job.getSha256(),
                job.getStorageKey(),
                job.getLanguages(),
                job.getStatus().name(),
                job.getPageCount(),
                job.getFullText(),
                pagesToJson(job.getPages()),
                job.getDurationMs(),
                job.getError(),
                Timestamp.from(job.getCreatedAt()),
                Timestamp.from(Instant.now()),
                timestamp(job.getCompletedAt()),
                timestamp(job.getRetentionUntil()),
                job.getLineageId(),
                job.getSourceDocumentId());
    }

    public boolean update(ExtractionJob job) {
        int updated = jdbc.update("""
                        UPDATE documents
                        SET status = ?, page_count = ?, full_text = ?, pages_json = ?,
                            duration_ms = ?, error = ?, updated_at = ?, completed_at = ?
                        WHERE id = ?
                        """,
                job.getStatus().name(),
                job.getPageCount(),
                job.getFullText(),
                pagesToJson(job.getPages()),
                job.getDurationMs(),
                job.getError(),
                Timestamp.from(Instant.now()),
                timestamp(job.getCompletedAt()),
                job.getId());
        return updated > 0;
    }

    public Optional<ExtractionJob> findById(UUID id) {
        return jdbc.query("SELECT " + DOCUMENT_COLUMNS + " FROM documents WHERE id = ?",
                (rs, rowNum) -> mapJob(rs),
                id)
                .stream()
                .findFirst();
    }

    public Optional<DocumentSummary> findSummaryById(UUID id) {
        return jdbc.query("""
                        SELECT id, file_name, mime_type, status, page_count, size_bytes,
                               languages, duration_ms, error, created_at, completed_at
                        FROM documents
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapSummary(rs),
                id)
                .stream()
                .findFirst();
    }

    public List<DocumentSummary> list(
            UUID organizationId,
            UUID ownerId,
            String query,
            ExtractionStatus status,
            int offset,
            int limit) {
        return list(organizationId, ownerId, query, status == null ? null : List.of(status), null, null, offset, limit);
    }

    public List<DocumentSummary> list(
            UUID organizationId,
            UUID ownerId,
            String query,
            List<ExtractionStatus> statuses,
            Instant from,
            Instant to,
            int offset,
            int limit) {
        QueryParts parts = queryParts(organizationId, ownerId, query, statuses, from, to);
        List<Object> args = new ArrayList<>(parts.args());
        args.add(limit);
        args.add(offset);

        return jdbc.query("""
                        SELECT id, file_name, mime_type, status, page_count, size_bytes,
                               languages, duration_ms, error, created_at, completed_at
                        FROM documents
                        """ + parts.where() + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapSummary(rs),
                args.toArray());
    }

    public long count(
            UUID organizationId,
            UUID ownerId,
            String query,
            ExtractionStatus status) {
        return count(organizationId, ownerId, query, status == null ? null : List.of(status), null, null);
    }

    public long count(
            UUID organizationId,
            UUID ownerId,
            String query,
            List<ExtractionStatus> statuses,
            Instant from,
            Instant to) {
        QueryParts parts = queryParts(organizationId, ownerId, query, statuses, from, to);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents " + parts.where(),
                Long.class,
                parts.args().toArray());
        return count == null ? 0 : count;
    }

    public boolean delete(UUID id) {
        return jdbc.update("DELETE FROM documents WHERE id = ?", id) > 0;
    }

    public int deleteAll(
            UUID organizationId,
            UUID ownerId,
            ExtractionStatus status) {
        StringBuilder sql = new StringBuilder("DELETE FROM documents WHERE organization_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        if (ownerId != null) {
            sql.append(" AND owner_id = ?");
            args.add(ownerId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        return jdbc.update(sql.toString(), args.toArray());
    }

    public List<StorageKeyRecord> findStorageKeys(
            UUID organizationId,
            UUID ownerId,
            ExtractionStatus status) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, storage_key FROM documents WHERE organization_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        if (ownerId != null) {
            sql.append(" AND owner_id = ?");
            args.add(ownerId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        return jdbc.query(sql.toString(),
                (rs, rowNum) -> new StorageKeyRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("storage_key")),
                args.toArray());
    }

    public List<ExpiredDocument> findExpired(Instant now, int limit) {
        return jdbc.query("""
                        SELECT id, organization_id, owner_id, storage_key, file_name
                        FROM documents
                        WHERE retention_until IS NOT NULL AND retention_until < ?
                        ORDER BY retention_until
                        LIMIT ?
                        """,
                (rs, rowNum) -> new ExpiredDocument(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("storage_key"),
                        rs.getString("file_name")),
                Timestamp.from(now), limit);
    }

    public long totalForOrganization(UUID organizationId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE organization_id = ?",
                Long.class, organizationId);
        return count == null ? 0 : count;
    }

    private QueryParts queryParts(
            UUID organizationId,
            UUID ownerId,
            String query,
            ExtractionStatus status) {
        return queryParts(organizationId, ownerId, query, status == null ? null : List.of(status), null, null);
    }

    private QueryParts queryParts(
            UUID organizationId,
            UUID ownerId,
            String query,
            List<ExtractionStatus> statuses,
            Instant from,
            Instant to) {
        StringBuilder where = new StringBuilder("WHERE organization_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(organizationId);

        if (ownerId != null) {
            where.append(" AND owner_id = ?");
            args.add(ownerId);
        }
        if (statuses != null && !statuses.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(statuses.size(), "?"));
            where.append(" AND status IN (").append(placeholders).append(")");
            for (ExtractionStatus s : statuses) {
                args.add(s.name());
            }
        }
        if (from != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND created_at <= ?");
            args.add(Timestamp.from(to));
        }
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            where.append(" AND (LOWER(file_name) LIKE ? OR COALESCE(LOWER(full_text), '') LIKE ?)");
            args.add(like);
            args.add(like);
        }
        return new QueryParts(where.toString(), args);
    }

    private ExtractionJob mapJob(ResultSet rs) throws SQLException {
        ExtractionJob job = new ExtractionJob(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("file_name"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getString("storage_key"),
                rs.getString("languages"),
                instant(rs, "created_at"),
                instant(rs, "retention_until"),
                rs.getObject("lineage_id", UUID.class),
                rs.getObject("source_document_id", UUID.class));
        job.restore(
                ExtractionStatus.valueOf(rs.getString("status")),
                rs.getInt("page_count"),
                rs.getString("full_text"),
                pagesFromJson(rs.getString("pages_json")),
                rs.getLong("duration_ms"),
                rs.getString("error"),
                instant(rs, "completed_at"));
        return job;
    }

    private DocumentSummary mapSummary(ResultSet rs) throws SQLException {
        return new DocumentSummary(
                rs.getObject("id", UUID.class),
                rs.getString("file_name"),
                rs.getString("mime_type"),
                ExtractionStatus.valueOf(rs.getString("status")),
                rs.getInt("page_count"),
                rs.getLong("size_bytes"),
                rs.getString("languages"),
                rs.getLong("duration_ms"),
                rs.getString("error"),
                instant(rs, "created_at"),
                instant(rs, "completed_at"));
    }

    private String pagesToJson(List<PageResult> pages) {
        try {
            return objectMapper.writeValueAsString(pages == null ? List.of() : pages);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<PageResult> pagesFromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<PageResult>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record QueryParts(String where, List<Object> args) {
    }
}
