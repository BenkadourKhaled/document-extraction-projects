package dz.DocuScan.document.repository;

import dz.DocuScan.document.dto.AdminUserView;
import dz.DocuScan.document.dto.UserView;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    public record UserAccount(
            UUID id,
            UUID organizationId,
            String organizationName,
            String email,
            String passwordHash,
            String displayName,
            Role role,
            boolean enabled,
            int tokenVersion) {
    }

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("""
                        SELECT u.id, u.email, u.password_hash, u.display_name, u.enabled,
                               u.token_version, m.organization_id, o.name AS organization_name, m.role
                        FROM users u
                        JOIN memberships m ON m.user_id = u.id
                        JOIN organizations o ON o.id = m.organization_id
                        WHERE LOWER(u.email) = LOWER(?)
                        ORDER BY m.created_at
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapAccount(rs),
                email)
                .stream()
                .findFirst();
    }

    public Optional<AuthenticatedUser> findAuthenticated(
            UUID userId,
            UUID organizationId,
            int tokenVersion) {
        return jdbc.query("""
                        SELECT u.id, u.email, u.display_name, u.enabled,
                               m.organization_id, o.name AS organization_name, m.role
                        FROM users u
                        JOIN memberships m ON m.user_id = u.id
                        JOIN organizations o ON o.id = m.organization_id
                        WHERE u.id = ? AND m.organization_id = ? AND u.enabled = TRUE
                          AND u.token_version = ?
                        """,
                (rs, rowNum) -> new AuthenticatedUser(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("organization_name"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        Role.valueOf(rs.getString("role"))),
                userId, organizationId, tokenVersion)
                .stream()
                .findFirst();
    }

    public Optional<UserView> findView(UUID userId, UUID organizationId) {
        return findAuthenticated(userId, organizationId, currentTokenVersion(userId))
                .map(user -> new UserView(
                        user.id(),
                        user.organizationId(),
                        user.organizationName(),
                        user.email(),
                        user.displayName(),
                        user.role()));
    }

    public int currentTokenVersion(UUID userId) {
        Integer version = jdbc.queryForObject(
                "SELECT token_version FROM users WHERE id = ?",
                Integer.class,
                userId);
        return version == null ? 0 : version;
    }

    public List<AdminUserView> listByOrganization(UUID organizationId) {
        return jdbc.query("""
                        SELECT u.id, u.email, u.display_name, u.enabled, u.created_at, m.role
                        FROM users u
                        JOIN memberships m ON m.user_id = u.id
                        WHERE m.organization_id = ?
                        ORDER BY u.display_name, u.email
                        """,
                (rs, rowNum) -> new AdminUserView(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        Role.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant()),
                organizationId);
    }

    public Optional<AdminUserView> findInOrganization(UUID userId, UUID organizationId) {
        return jdbc.query("""
                        SELECT u.id, u.email, u.display_name, u.enabled, u.created_at, m.role
                        FROM users u
                        JOIN memberships m ON m.user_id = u.id
                        WHERE u.id = ? AND m.organization_id = ?
                        """,
                (rs, rowNum) -> new AdminUserView(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        Role.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant()),
                userId, organizationId)
                .stream()
                .findFirst();
    }

    public long countUsers() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count == null ? 0 : count;
    }

    public boolean existsByEmail(String email) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)",
                Long.class, email);
        return count != null && count > 0;
    }

    public boolean existsByEmailAndNotId(String email, UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?) AND id <> ?",
                Long.class, email, userId);
        return count != null && count > 0;
    }

    public void createOrganization(UUID id, String name, String slug, Instant createdAt) {
        jdbc.update("INSERT INTO organizations (id, name, slug, created_at) VALUES (?, ?, ?, ?)",
                id, name, slug, Timestamp.from(createdAt));
    }

    public void createUser(
            UUID id,
            String email,
            String passwordHash,
            String displayName,
            boolean enabled,
            Instant createdAt) {
        jdbc.update("""
                        INSERT INTO users (id, email, password_hash, display_name, enabled, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id, email, passwordHash, displayName, enabled, Timestamp.from(createdAt));
    }

    public void createMembership(
            UUID id,
            UUID userId,
            UUID organizationId,
            Role role,
            Instant createdAt) {
        jdbc.update("""
                        INSERT INTO memberships (id, user_id, organization_id, role, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id, userId, organizationId, role.name(), Timestamp.from(createdAt));
    }

    public void updatePassword(UUID userId, String passwordHash) {
        jdbc.update(
                "UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?",
                passwordHash,
                userId);
    }

    public void revokeSessions(UUID userId) {
        jdbc.update(
                "UPDATE users SET token_version = token_version + 1 WHERE id = ?",
                userId);
    }

    public void updateProfile(UUID userId, String email, String displayName) {
        jdbc.update(
                "UPDATE users SET email = ?, display_name = ? WHERE id = ?",
                email,
                displayName,
                userId);
    }

    public void updateRole(UUID userId, UUID organizationId, Role role) {
        jdbc.update(
                "UPDATE memberships SET role = ? WHERE user_id = ? AND organization_id = ?",
                role.name(), userId, organizationId);
    }

    public void updateEnabled(UUID userId, boolean enabled) {
        jdbc.update("UPDATE users SET enabled = ? WHERE id = ?", enabled, userId);
    }

    private UserAccount mapAccount(ResultSet rs) throws SQLException {
        return new UserAccount(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("organization_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("enabled"),
                rs.getInt("token_version"));
    }
}

