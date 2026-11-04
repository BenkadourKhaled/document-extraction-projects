package dz.DocuScan.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt, LoginProtection loginProtection) {

    public record Jwt(String secret, String issuer, long ttlMinutes) {
    }

    public record LoginProtection(
            int maxAttempts,
            int windowMinutes,
            int blockMinutes) {
    }
}
