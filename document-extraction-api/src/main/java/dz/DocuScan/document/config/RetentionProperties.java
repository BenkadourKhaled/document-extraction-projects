package dz.DocuScan.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(int defaultDays, boolean purgeEnabled) {
}
