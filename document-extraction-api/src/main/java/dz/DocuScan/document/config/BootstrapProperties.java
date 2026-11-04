package dz.DocuScan.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String organizationName,
        String organizationSlug,
        String adminEmail,
        String adminPassword,
        String adminDisplayName) {
}
