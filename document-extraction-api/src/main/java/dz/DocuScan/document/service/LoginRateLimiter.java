package dz.DocuScan.document.service;

import dz.DocuScan.document.config.SecurityProperties;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.repository.LoginAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final LoginAttemptRepository attempts;
    private final SecurityProperties properties;

    public LoginRateLimiter(
            LoginAttemptRepository attempts,
            SecurityProperties properties) {
        this.attempts = attempts;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public void assertAllowed(String email, String ipAddress) {
        Instant now = Instant.now();
        LoginAttemptRepository.LoginAttempt current =
                attempts.find(key(email, ipAddress)).orElse(null);
        if (current != null
                && current.blockedUntil() != null
                && current.blockedUntil().isAfter(now)) {
            throw new DocumentException(
                    429,
                    "Trop de tentatives de connexion. Reessayez dans quelques minutes.");
        }
    }

    @Transactional
    public void recordFailure(String email, String ipAddress) {
        String key = key(email, ipAddress);
        Instant now = Instant.now();
        SecurityProperties.LoginProtection config = properties.loginProtection();
        LoginAttemptRepository.LoginAttempt current = attempts.find(key).orElse(null);

        boolean withinWindow = current != null
                && current.updatedAt().isAfter(now.minus(Duration.ofMinutes(config.windowMinutes())));
        int nextAttempts = withinWindow ? current.attempts() + 1 : 1;
        Instant blockedUntil = nextAttempts >= Math.max(1, config.maxAttempts())
                ? now.plus(Duration.ofMinutes(Math.max(1, config.blockMinutes())))
                : null;
        attempts.save(key, nextAttempts, blockedUntil, now);
    }

    @Transactional
    public void recordSuccess(String email, String ipAddress) {
        attempts.delete(key(email, ipAddress));
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldEntries() {
        int deleted = attempts.deleteOlderThan(Instant.now().minus(Duration.ofDays(2)));
        if (deleted > 0) {
            log.info("Purged {} stale login attempt record(s).", deleted);
        }
    }

    private String key(String email, String ipAddress) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (normalizedEmail + "|" + normalizedIp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
