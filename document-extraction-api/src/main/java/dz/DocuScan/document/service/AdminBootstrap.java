package dz.DocuScan.document.service;

import dz.DocuScan.document.config.BootstrapProperties;
import dz.DocuScan.document.model.Role;
import dz.DocuScan.document.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(
            BootstrapProperties properties,
            UserRepository users,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || users.countUsers() > 0) {
            return;
        }
        if (properties.adminPassword() == null || properties.adminPassword().isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD est obligatoire pour creer le premier administrateur.");
        }
        if (properties.adminPassword().length() < PasswordPolicy.MIN_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD doit contenir au moins "
                            + PasswordPolicy.MIN_LENGTH + " caracteres.");
        }

        Instant now = Instant.now();
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        users.createOrganization(
                organizationId,
                properties.organizationName(),
                properties.organizationSlug(),
                now);
        users.createUser(
                userId,
                properties.adminEmail().trim().toLowerCase(),
                passwordEncoder.encode(properties.adminPassword()),
                properties.adminDisplayName(),
                true,
                now);
        users.createMembership(UUID.randomUUID(), userId, organizationId, Role.ADMIN, now);

        log.info("Compte administrateur initial cree: {}", properties.adminEmail());
        if ("ChangeMe123!".equals(properties.adminPassword())) {
            log.warn("Le mot de passe administrateur par defaut est utilise. "
                    + "Definissez BOOTSTRAP_ADMIN_PASSWORD avant la mise en production.");
        }
    }
}
