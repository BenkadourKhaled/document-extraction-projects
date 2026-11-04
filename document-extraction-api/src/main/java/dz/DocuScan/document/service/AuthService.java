package dz.DocuScan.document.service;

import dz.DocuScan.document.dto.LoginResponse;
import dz.DocuScan.document.dto.UserView;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.repository.UserRepository;
import dz.DocuScan.document.security.JwtService;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            LoginRateLimiter loginRateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginResponse login(
            String email,
            String password,
            String ipAddress,
            String userAgent) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        loginRateLimiter.assertAllowed(normalizedEmail, ipAddress);

        UserRepository.UserAccount account = users.findByEmail(normalizedEmail).orElse(null);
        String hash = account == null ? DUMMY_HASH : account.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(password, hash);

        if (account == null || !passwordMatches || !account.enabled()) {
            loginRateLimiter.recordFailure(normalizedEmail, ipAddress);
            throw new DocumentException(401, "Email ou mot de passe incorrect.");
        }

        AuthenticatedUser user = new AuthenticatedUser(
                account.id(),
                account.organizationId(),
                account.organizationName(),
                account.email(),
                account.displayName(),
                account.role());

        loginRateLimiter.recordSuccess(normalizedEmail, ipAddress);

        auditService.record(
                user, "AUTH_LOGIN", null, ipAddress, userAgent,
                Map.of("email", user.email()));

        return new LoginResponse(
                jwtService.issue(user, account.tokenVersion()),
                "Bearer",
                jwtService.ttlSeconds(),
                toView(user));
    }

    public UserView me(AuthenticatedUser user) {
        return toView(user);
    }

    public void logout(
            AuthenticatedUser user,
            String ipAddress,
            String userAgent) {
        // Revoke every outstanding token for this account. A new login issues a
        // token with the incremented version.
        users.revokeSessions(user.id());
        auditService.record(user, "AUTH_LOGOUT", null, ipAddress, userAgent, Map.of());
    }

    private UserView toView(AuthenticatedUser user) {
        return new UserView(
                user.id(),
                user.organizationId(),
                user.organizationName(),
                user.email(),
                user.displayName(),
                user.role());
    }
}
