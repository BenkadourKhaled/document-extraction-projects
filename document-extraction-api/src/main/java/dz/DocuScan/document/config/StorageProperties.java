package dz.DocuScan.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String localPath,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        boolean secure) {
}
