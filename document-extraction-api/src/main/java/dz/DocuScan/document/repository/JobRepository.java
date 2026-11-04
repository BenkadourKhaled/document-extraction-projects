package dz.DocuScan.document.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JobRepository {

    public record JobRecord(UUID id, UUID documentId, int attempts) {
    }

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(UUID jobId, UUID documentId, Instant now) {
        jdbc.update("""
                        INSERT INTO jobs (
                            id, document_id, status, attempts, available_at,
                            created_at, updated_at
                        ) VALUES (?, ?, 'PENDING', 0, ?, ?, ?)
                        """,
                jobId, documentId, Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
    }

    /**
     * Claims one pending job. The row lock guarantees that two workers cannot
     * process the same document. SKIP LOCKED is intentionally avoided so the
     * same SQL remains valid on H2 for local development.
     */
    @Transactional
    public Optional<JobRecord> claimNext(String workerId, Instant now) {
        List<JobRecord> jobs = jdbc.query("""
                        SELECT id, document_id, attempts
                        FROM jobs
                        WHERE status = 'PENDING' AND available_at <= ?
                        ORDER BY created_at
                        LIMIT 1
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new JobRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getInt("attempts")),
                Timestamp.from(now));

        if (jobs.isEmpty()) return Optional.empty();

        JobRecord job = jobs.get(0);
        int updated = jdbc.update("""
                        UPDATE jobs
                        SET status = 'PROCESSING', locked_at = ?, locked_by = ?, updated_at = ?
                        WHERE id = ? AND status = 'PENDING'
                        """,
                Timestamp.from(now), workerId, Timestamp.from(now), job.id());

        return updated == 1 ? Optional.of(job) : Optional.empty();
    }

    public void complete(UUID jobId) {
        jdbc.update("""
                        UPDATE jobs
                        SET status = 'COMPLETED', locked_at = NULL, locked_by = NULL,
                            last_error = NULL, updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(Instant.now()), jobId);
    }

    public void retry(UUID jobId, String error, Instant availableAt) {
        jdbc.update("""
                        UPDATE jobs
                        SET status = 'PENDING', attempts = attempts + 1, available_at = ?,
                            locked_at = NULL, locked_by = NULL, last_error = ?, updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(availableAt), truncate(error, 1000),
                Timestamp.from(Instant.now()), jobId);
    }

    public void fail(UUID jobId, String error) {
        jdbc.update("""
                        UPDATE jobs
                        SET status = 'FAILED', locked_at = NULL, locked_by = NULL,
                            last_error = ?, updated_at = ?
                        WHERE id = ?
                        """,
                truncate(error, 1000), Timestamp.from(Instant.now()), jobId);
    }

    public int recoverStuck(Instant cutoff) {
        return jdbc.update("""
                        UPDATE jobs
                        SET status = 'PENDING', available_at = ?, locked_at = NULL,
                            locked_by = NULL, updated_at = ?
                        WHERE status = 'PROCESSING' AND locked_at < ?
                        """,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
                Timestamp.from(cutoff));
    }

    public long countByStatus(String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE status = ?",
                Long.class, status);
        return count == null ? 0 : count;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
