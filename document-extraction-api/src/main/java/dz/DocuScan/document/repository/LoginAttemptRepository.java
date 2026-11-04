package dz.DocuScan.document.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAttemptRepository {

    public record LoginAttempt(int attempts, Instant blockedUntil, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;

    public LoginAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LoginAttempt> find(String key) {
        return jdbc.query("""
                        SELECT attempts, blocked_until, updated_at
                        FROM login_attempts
                        WHERE attempt_key = ?
                        """,
                (rs, rowNum) -> new LoginAttempt(
                        rs.getInt("attempts"),
                        rs.getTimestamp("blocked_until") == null
                                ? null
                                : rs.getTimestamp("blocked_until").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                key)
                .stream()
                .findFirst();
    }

    public void save(String key, int attempts, Instant blockedUntil, Instant updatedAt) {
        int updated = jdbc.update("""
                        UPDATE login_attempts
                        SET attempts = ?, blocked_until = ?, updated_at = ?
                        WHERE attempt_key = ?
                        """,
                attempts,
                blockedUntil == null ? null : Timestamp.from(blockedUntil),
                Timestamp.from(updatedAt),
                key);
        if (updated == 0) {
            jdbc.update("""
                            INSERT INTO login_attempts (attempt_key, attempts, blocked_until, updated_at)
                            VALUES (?, ?, ?, ?)
                            """,
                    key,
                    attempts,
                    blockedUntil == null ? null : Timestamp.from(blockedUntil),
                    Timestamp.from(updatedAt));
        }
    }

    public void delete(String key) {
        jdbc.update("DELETE FROM login_attempts WHERE attempt_key = ?", key);
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM login_attempts WHERE updated_at < ?",
                Timestamp.from(cutoff));
    }
}
