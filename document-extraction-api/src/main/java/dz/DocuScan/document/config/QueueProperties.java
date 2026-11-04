package dz.DocuScan.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.queue")
public record QueueProperties(
        boolean workerEnabled,
        long pollIntervalMs,
        int maxAttempts,
        String workerId) {
}
