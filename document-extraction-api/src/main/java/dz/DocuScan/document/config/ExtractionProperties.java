package dz.DocuScan.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app.extraction")
public record ExtractionProperties(
        long maxFileSizeBytes, int maxPages, int nativeTextMinCharacters, int pdfDpi,
        String defaultLanguages, List<String> supportedLanguages, String tesseractDatapath,
        long cacheTtlHours, long cacheMaximumSize, boolean asyncEnabled,
        long asyncThresholdBytes, int asyncThresholdPages, int asyncCorePoolSize,
        int asyncMaxPoolSize, int asyncQueueCapacity) { }
