package dz.DocuScan.document.service;

import dz.DocuScan.document.config.RetentionProperties;
import dz.DocuScan.document.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.retention.purge-enabled", havingValue = "true")
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RetentionProperties properties;
    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final AuditService auditService;

    public RetentionService(
            RetentionProperties properties,
            DocumentRepository documents,
            DocumentStorage storage,
            AuditService auditService) {
        this.properties = properties;
        this.documents = documents;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 15 * * * *")
    public void purge() {
        if (properties.defaultDays() <= 0) return;

        List<DocumentRepository.ExpiredDocument> expired =
                documents.findExpired(Instant.now(), 100);
        for (DocumentRepository.ExpiredDocument document : expired) {
            try {
                storage.delete(document.storageKey());
            } catch (RuntimeException ex) {
                log.warn("Unable to delete expired file {}: {}", document.storageKey(), ex.getMessage());
            }
            documents.delete(document.id());
            auditService.record(
                    document.organizationId(),
                    null,
                    "DOCUMENT_RETENTION_DELETED",
                    document.id(),
                    null,
                    "retention-worker",
                    Map.of("fileName", document.fileName()));
        }
        if (!expired.isEmpty()) {
            log.info("Purged {} expired document(s).", expired.size());
        }
    }
}
