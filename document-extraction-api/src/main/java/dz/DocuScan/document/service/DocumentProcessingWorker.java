package dz.DocuScan.document.service;

import dz.DocuScan.document.config.QueueProperties;
import dz.DocuScan.document.field.FieldExtractionService;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.repository.JobRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.queue.worker-enabled", havingValue = "true", matchIfMissing = true)
public class DocumentProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final JobRepository jobs;
    private final ExtractionResultStore store;
    private final DocumentStorage storage;
    private final ExtractionService extractionService;
    private final FieldExtractionService fieldExtractionService;
    private final QueueProperties properties;

    public DocumentProcessingWorker(
            JobRepository jobs,
            ExtractionResultStore store,
            DocumentStorage storage,
            ExtractionService extractionService,
            FieldExtractionService fieldExtractionService,
            QueueProperties properties) {
        this.jobs = jobs;
        this.store = store;
        this.storage = storage;
        this.extractionService = extractionService;
        this.fieldExtractionService = fieldExtractionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.queue.poll-interval-ms:1500}")
    public void poll() {
        Optional<JobRepository.JobRecord> claimed =
                jobs.claimNext(properties.workerId(), Instant.now());
        if (claimed.isEmpty()) return;

        JobRepository.JobRecord job = claimed.get();
        try {
            Optional<ExtractionJob> document = store.findById(job.documentId());
            if (document.isEmpty()) {
                jobs.complete(job.id());
                return;
            }
            ExtractionJob extraction = document.get();
            byte[] content = storage.get(extraction.getStorageKey());
            extractionService.processJob(extraction, content, extraction.getLanguages(),
                    completed -> {
                        try {
                            fieldExtractionService.extractAndStore(completed);
                        } catch (Exception ex) {
                            // The document result remains valid; field extraction can be
                            // retried independently without inventing a field.
                            log.warn("Field extraction failed for document {}: {}",
                                    completed.getId(), ex.getClass().getSimpleName());
                        }
                    });

            if (extraction.getStatus() == ExtractionStatus.COMPLETED) {
                jobs.complete(job.id());
            } else {
                jobs.fail(job.id(), extraction.getError());
            }
        } catch (Exception ex) {
            handleFailure(job, ex);
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void recoverStuckJobs() {
        int recovered = jobs.recoverStuck(Instant.now().minus(10, ChronoUnit.MINUTES));
        if (recovered > 0) {
            log.warn("Recovered {} stuck OCR job(s).", recovered);
        }
    }

    private void handleFailure(JobRepository.JobRecord job, Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        int attempts = job.attempts() + 1;
        if (attempts < properties.maxAttempts()) {
            Instant retryAt = Instant.now().plusSeconds(Math.min(60, attempts * 5L));
            jobs.retry(job.id(), message, retryAt);
            log.warn("OCR job {} failed (attempt {}), retrying at {}: {}",
                    job.id(), attempts, retryAt, message);
            return;
        }

        jobs.fail(job.id(), message);
        store.findById(job.documentId()).ifPresent(document -> {
            document.fail(message, 0);
            store.save(document);
        });
        log.error("OCR job {} failed permanently: {}", job.id(), message);
    }
}
