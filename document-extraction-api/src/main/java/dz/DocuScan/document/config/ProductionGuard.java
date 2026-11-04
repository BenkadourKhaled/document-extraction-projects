package dz.DocuScan.document.config;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Prevents a production deployment from starting with development secrets.
 * The profile "postgres" is used by the supplied Docker Compose stack.
 */
@Component
public class ProductionGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionGuard.class);
    private static final String DEFAULT_JWT_SECRET =
            "dev-only-secret-change-me-please-0123456789abcdef";
    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";

    private final Environment environment;
    private final SecurityProperties security;
    private final BootstrapProperties bootstrap;
    private final StorageProperties storage;
    private final String corsOrigin;

    public ProductionGuard(
            Environment environment,
            SecurityProperties security,
            BootstrapProperties bootstrap,
            StorageProperties storage,
            @Value("${app.cors.allowed-origin}") String corsOrigin) {
        this.environment = environment;
        this.security = security;
        this.bootstrap = bootstrap;
        this.storage = storage;
        this.corsOrigin = corsOrigin;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "postgres"));
        if (!production) {
            if (isUnsafeSecret(security.jwt().secret(), DEFAULT_JWT_SECRET)) {
                log.warn("JWT_SECRET de developpement actif. Ne pas utiliser en production.");
            }
            return;
        }

        requireSafe("JWT_SECRET", security.jwt().secret(), DEFAULT_JWT_SECRET);
        if (bootstrap.enabled()) {
            requireSafe("BOOTSTRAP_ADMIN_PASSWORD",
                    bootstrap.adminPassword(), DEFAULT_ADMIN_PASSWORD);
            if (bootstrap.adminEmail() != null
                    && bootstrap.adminEmail().toLowerCase(Locale.ROOT).equals("admin@local.test")) {
                log.warn("BOOTSTRAP_ADMIN_EMAIL utilise l'adresse de demonstration admin@local.test.");
            }
        }
        if ("s3".equalsIgnoreCase(storage.type())) {
            requireSafe("MINIO_SECRET_KEY", storage.secretKey(), "minioadmin");
        }
        if (corsOrigin != null && corsOrigin.contains("localhost")) {
            log.warn("CORS_ALLOWED_ORIGIN pointe vers localhost en production: {}", corsOrigin);
        }
        log.info("Production secret checks passed.");
    }

    private void requireSafe(String name, String value, String forbidden) {
        if (isUnsafeSecret(value, forbidden)) {
            throw new IllegalStateException(
                    name + " doit etre remplace par une valeur aleatoire avant la production.");
        }
    }

    private boolean isUnsafeSecret(String value, String forbidden) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return value.equals(forbidden)
                || normalized.contains("change-me")
                || normalized.contains("dev-only-secret")
                || normalized.contains("minioadmin")
                || normalized.contains("admin123");
    }
}
