package dz.DocuScan.document.repository;

import dz.DocuScan.document.dto.AuditEventView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID organizationId,
            UUID actorId,
            String action,
            UUID documentId,
            String ipAddress,
            String userAgent,
            String metadata) {
        jdbc.update("""
                        INSERT INTO audit_events (
                            id, organization_id, actor_id, action, document_id,
                            ip_address, user_agent, metadata, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), organizationId, actorId, action, documentId,
                ipAddress, truncate(userAgent, 500), metadata, Timestamp.from(Instant.now()));
    }

    public List<AuditEventView> latest(UUID organizationId, int limit) {
        return jdbc.query("""
                        SELECT id, actor_id, action, document_id, ip_address,
                               user_agent, metadata, created_at
                        FROM audit_events
                        WHERE organization_id = ?
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> map(rs),
                organizationId, limit);
    }

    private AuditEventView map(ResultSet rs) throws SQLException {
        return new AuditEventView(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("action"),
                rs.getObject("document_id", UUID.class),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getString("metadata"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
