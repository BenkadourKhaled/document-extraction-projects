package dz.DocuScan.document.service;

import dz.DocuScan.document.dto.AdminUserView;
import dz.DocuScan.document.dto.CreateUserRequest;
import dz.DocuScan.document.dto.ResetPasswordRequest;
import dz.DocuScan.document.dto.UpdateProfileRequest;
import dz.DocuScan.document.dto.UpdateUserRequest;
import dz.DocuScan.document.dto.UserView;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.Role;
import dz.DocuScan.document.repository.UserRepository;
import dz.DocuScan.document.web.RequestInfo;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<AdminUserView> list(AuthenticatedUser actor) {
        requireAdmin(actor);
        return users.listByOrganization(actor.organizationId());
    }

    @Transactional
    public UserView create(
            CreateUserRequest request,
            AuthenticatedUser actor,
            RequestInfo info) {
        requireAdmin(actor);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().trim();
        PasswordPolicy.validate(request.password());
        if (users.existsByEmail(email)) {
            throw new DocumentException(409, "Un compte existe déjà pour cet email.");
        }

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            users.createUser(
                    userId,
                    email,
                    passwordEncoder.encode(request.password()),
                    displayName,
                    true,
                    now);
            users.createMembership(
                    UUID.randomUUID(),
                    userId,
                    actor.organizationId(),
                    request.role(),
                    now);
        } catch (DataIntegrityViolationException ex) {
            throw new DocumentException(409, "Un compte existe déjà pour cet email.", ex);
        }

        auditService.record(
                actor,
                "USER_CREATED",
                null,
                info.ipAddress(),
                info.userAgent(),
                Map.of(
                        "userId", userId.toString(),
                        "email", email,
                        "role", request.role().name()));

        return users.findView(userId, actor.organizationId())
                .orElseThrow(() -> new DocumentException(500, "Le compte créé est introuvable."));
    }

    @Transactional
    public AdminUserView update(
            UUID userId,
            UpdateUserRequest request,
            AuthenticatedUser actor,
            RequestInfo info) {
        requireAdmin(actor);

        if (request.role() == null && request.enabled() == null) {
            throw new DocumentException(400, "Aucune modification demandée.");
        }

        AdminUserView target = users.findInOrganization(userId, actor.organizationId())
                .orElseThrow(() -> new DocumentException(404, "Utilisateur introuvable."));

        boolean removesOwnAdminRole =
                target.id().equals(actor.id())
                        && ((request.role() != null && request.role() != Role.ADMIN)
                        || Boolean.FALSE.equals(request.enabled()));
        if (removesOwnAdminRole) {
            throw new DocumentException(
                    400,
                    "Vous ne pouvez pas retirer vos propres droits d'administration.");
        }

        if (request.role() != null && request.role() != target.role()) {
            users.updateRole(userId, actor.organizationId(), request.role());
        }
        if (request.enabled() != null && request.enabled() != target.enabled()) {
            users.updateEnabled(userId, request.enabled());
        }

        auditService.record(
                actor,
                "USER_UPDATED",
                null,
                info.ipAddress(),
                info.userAgent(),
                Map.of(
                        "userId", userId.toString(),
                        "role", request.role() == null ? target.role().name() : request.role().name(),
                        "enabled", request.enabled() == null ? target.enabled() : request.enabled()));

        return users.findInOrganization(userId, actor.organizationId())
                .orElseThrow(() -> new DocumentException(404, "Utilisateur introuvable."));
    }

    @Transactional
    public void changePassword(
            String currentPassword,
            String newPassword,
            AuthenticatedUser actor,
            RequestInfo info) {
        UserRepository.UserAccount account = users.findByEmail(actor.email())
                .orElseThrow(() -> new DocumentException(404, "Compte introuvable."));

        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new DocumentException(400, "Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(newPassword, account.passwordHash())) {
            throw new DocumentException(400, "Le nouveau mot de passe doit être différent.");
        }

        PasswordPolicy.validate(newPassword);
        users.updatePassword(actor.id(), passwordEncoder.encode(newPassword));
        auditService.record(
                actor,
                "PASSWORD_CHANGED",
                null,
                info.ipAddress(),
                info.userAgent(),
                Map.of());
    }

    @Transactional
    public UserView updateProfile(
            UpdateProfileRequest request,
            AuthenticatedUser actor,
            RequestInfo info) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().trim();
        if (users.existsByEmailAndNotId(email, actor.id())) {
            throw new DocumentException(409, "Un compte existe déjà pour cet email.");
        }
        try {
            users.updateProfile(actor.id(), email, displayName);
        } catch (DataIntegrityViolationException ex) {
            throw new DocumentException(409, "Un compte existe déjà pour cet email.", ex);
        }

        auditService.record(
                actor,
                "USER_PROFILE_UPDATED",
                null,
                info.ipAddress(),
                info.userAgent(),
                Map.of("email", email, "displayName", displayName));

        return users.findView(actor.id(), actor.organizationId())
                .orElseThrow(() -> new DocumentException(404, "Compte introuvable."));
    }

    @Transactional
    public void resetPassword(
            UUID userId,
            ResetPasswordRequest request,
            AuthenticatedUser actor,
            RequestInfo info) {
        requireAdmin(actor);
        if (actor.id().equals(userId)) {
            throw new DocumentException(
                    400,
                    "Utilisez le changement de mot de passe pour votre propre compte.");
        }

        AdminUserView target = users.findInOrganization(userId, actor.organizationId())
                .orElseThrow(() -> new DocumentException(404, "Utilisateur introuvable."));

        PasswordPolicy.validate(request.newPassword());
        users.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        auditService.record(
                actor,
                "USER_PASSWORD_RESET",
                null,
                info.ipAddress(),
                info.userAgent(),
                Map.of("userId", target.id().toString(), "email", target.email()));
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != Role.ADMIN) {
            throw new DocumentException(403, "Droits administrateur requis.");
        }
    }
}
