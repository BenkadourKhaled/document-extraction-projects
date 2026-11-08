package dz.DocuScan.document.service;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.config.RetentionProperties;
import dz.DocuScan.document.dto.DocumentSummary;
import dz.DocuScan.document.dto.ExtractionResponse;
import dz.DocuScan.document.dto.PageResponse;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.extractor.DocumentTypeDetector;
import dz.DocuScan.document.extractor.OcrExtractor;
import dz.DocuScan.document.extractor.PdfTextExtractor;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.repository.DocumentRepository;
import dz.DocuScan.document.repository.JobRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    public record FileContent(byte[] content, String mimeType, String fileName) {
    }

    private final ExtractionResultStore store;
    private final DocumentRepository documents;
    private final JobRepository jobs;
    private final DocumentStorage storage;
    private final AuditService auditService;
    private final DocumentTypeDetector detector;
    private final PdfTextExtractor pdf;
    private final OcrExtractor ocr;
    private final ExtractionProperties properties;
    private final RetentionProperties retention;

    public ExtractionService(
            ExtractionResultStore store,
            DocumentRepository documents,
            JobRepository jobs,
            DocumentStorage storage,
            AuditService auditService,
            DocumentTypeDetector detector,
            PdfTextExtractor pdf,
            OcrExtractor ocr,
            ExtractionProperties properties,
            RetentionProperties retention) {
        this.store = store;
        this.documents = documents;
        this.jobs = jobs;
        this.storage = storage;
        this.auditService = auditService;
        this.detector = detector;
        this.pdf = pdf;
        this.ocr = ocr;
        this.properties = properties;
        this.retention = retention;
    }

    public ExtractionJob create(
            String fileName,
            byte[] bytes,
            String languages,
            AuthenticatedUser actor,
            String ipAddress,
            String userAgent) {
        if (actor == null) {
            throw new DocumentException(401, "Authentification requise.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new DocumentException(400, "Le fichier est vide.");
        }
        if (bytes.length > properties.maxFileSizeBytes()) {
            throw new DocumentException(413, "La taille maximale autorisee est depassee.");
        }

        String mime = detector.detect(bytes);
        String normalizedLanguages = normalizeLanguages(languages);
        String safeName = safeFileName(fileName);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String storageKey = storage.key(actor.organizationId(), id, safeName);

        storage.put(storageKey, bytes);

        ExtractionJob job = new ExtractionJob(
                id,
                actor.organizationId(),
                actor.id(),
                safeName,
                mime,
                bytes.length,
                sha256(bytes),
                storageKey,
                normalizedLanguages,
                now,
                retention.defaultDays() > 0
                        ? now.plus(retention.defaultDays(), ChronoUnit.DAYS)
                        : null);

        try {
            int pages = countPages(job, bytes);
            if (pages > properties.maxPages()) {
                throw new DocumentException(
                        400,
                        "Le document depasse la limite de " + properties.maxPages() + " pages.");
            }
            job.setPageCount(pages);

            store.save(job);
            jobs.enqueue(UUID.randomUUID(), job.getId(), now);
            auditService.record(
                    actor, "DOCUMENT_CREATED", job.getId(), ipAddress, userAgent,
                    Map.of("fileName", safeName, "sizeBytes", bytes.length,
                            "languages", normalizedLanguages));
            return job;
        } catch (RuntimeException ex) {
            safeDelete(storageKey);
            store.delete(id);
            throw ex;
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public ExtractionJob reprocess(
            UUID id,
            String languages,
            AuthenticatedUser actor,
            String ipAddress,
            String userAgent) {
        if (actor == null) {
            throw new DocumentException(401, "Authentification requise.");
        }
        ExtractionJob source = get(id, actor);
        byte[] bytes = storage.get(source.getStorageKey());
        if (bytes == null || bytes.length == 0) {
            throw new DocumentException(404, "Fichier source introuvable.");
        }

        String mime = source.getMimeType();
        String normalizedLanguages = normalizeLanguages(languages);
        String safeName = safeFileName(source.getFileName());
        Instant now = Instant.now();
        UUID newId = UUID.randomUUID();
        String storageKey = storage.key(actor.organizationId(), newId, safeName);

        storage.put(storageKey, bytes);

        ExtractionJob job = new ExtractionJob(
                newId,
                actor.organizationId(),
                actor.id(),
                safeName,
                mime,
                bytes.length,
                sha256(bytes),
                storageKey,
                normalizedLanguages,
                now,
                retention.defaultDays() > 0
                        ? now.plus(retention.defaultDays(), ChronoUnit.DAYS)
                        : null,
                source.getLineageId(),
                source.getId());

        try {
            int pages = countPages(job, bytes);
            if (pages > properties.maxPages()) {
                throw new DocumentException(
                        400,
                        "Le document depasse la limite de " + properties.maxPages() + " pages.");
            }
            job.setPageCount(pages);

            store.save(job);
            jobs.enqueue(UUID.randomUUID(), job.getId(), now);
            auditService.record(
                    actor, "DOCUMENT_REPROCESSED", job.getId(), ipAddress, userAgent,
                    Map.of("sourceId", source.getId().toString(), "fileName", safeName,
                            "languages", normalizedLanguages));
            return job;
        } catch (RuntimeException ex) {
            safeDelete(storageKey);
            store.delete(newId);
            throw ex;
        }
    }

    public int countPages(ExtractionJob job, byte[] bytes) {
        if ("application/pdf".equals(job.getMimeType())) {
            return pdf.pageCount(bytes);
        }
        return imagePageCount(bytes);
    }

    private int imagePageCount(byte[] bytes) {
        try (ImageInputStream input =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return 1;

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return 1;

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return Math.max(1, reader.getNumImages(true));
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return 1;
        }
    }

    public void processJob(ExtractionJob job, byte[] bytes, String languages) {
        if (store.findById(job.getId()).isEmpty()) return;

        long start = System.nanoTime();
        synchronized (job) {
            if (job.isCancelled()) return;
            job.start();
            store.save(job);
        }

        try {
            List<PageResult> pages = "application/pdf".equals(job.getMimeType())
                    ? pdf.extract(bytes, languages)
                    : ocr.extractImage(bytes, languages);

            if (job.isCancelled() || store.findById(job.getId()).isEmpty()) return;

            String fullText = pages.stream()
                    .map(PageResult::text)
                    .collect(Collectors.joining("\n\n"));

            job.complete(pages, fullText, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception ex) {
            if (!job.isCancelled()) {
                String message = ex instanceof DocumentException de
                        ? de.getMessage()
                        : "Le traitement du document a echoue.";
                job.fail(message, (System.nanoTime() - start) / 1_000_000);
                log.warn("OCR failed for document {}: {}", job.getId(), message);
            }
        } finally {
            synchronized (job) {
                if (!job.isCancelled() && store.findById(job.getId()).isPresent()) {
                    store.save(job);
                }
            }
        }
    }

    public ExtractionJob get(UUID id, AuthenticatedUser actor) {
        ExtractionJob job = store.findById(id)
                .orElseThrow(() -> new DocumentException(
                        404, "Extraction introuvable ou expiree."));
        requireAccess(job, actor);
        return job;
    }

    public FileContent loadFile(UUID id, AuthenticatedUser actor) {
        ExtractionJob job = get(id, actor);
        try {
            return new FileContent(
                    storage.get(job.getStorageKey()),
                    job.getMimeType(),
                    job.getFileName());
        } catch (RuntimeException ex) {
            throw new DocumentException(404, "Fichier source introuvable.", ex);
        }
    }

    public PageResponse<DocumentSummary> list(
            AuthenticatedUser actor,
            String query,
            String statusValue,
            int page,
            int size) {
        return list(actor, query, statusValue, page, size, null, null);
    }

    public PageResponse<DocumentSummary> list(
            AuthenticatedUser actor,
            String query,
            String statusValue,
            int page,
            int size,
            String from,
            String to) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<ExtractionStatus> statuses = parseStatuses(statusValue);
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        UUID ownerFilter = actor.role().canSeeOrganizationHistory() ? null : actor.id();
        List<DocumentSummary> items = documents.list(
                actor.organizationId(), ownerFilter, query, statuses, fromInstant, toInstant,
                safePage * safeSize, safeSize);
        long total = documents.count(actor.organizationId(), ownerFilter, query, statuses, fromInstant, toInstant);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public void delete(UUID id, AuthenticatedUser actor, String ipAddress, String userAgent) {
        ExtractionJob job = get(id, actor);
        synchronized (job) {
            if (job.getStatus() == ExtractionStatus.PENDING
                    || job.getStatus() == ExtractionStatus.PROCESSING) {
                job.cancel("Traitement annule.");
            }
        }
        safeDelete(job.getStorageKey());
        store.delete(id);
        auditService.record(
                actor, "DOCUMENT_DELETED", id, ipAddress, userAgent,
                Map.of("fileName", job.getFileName()));
    }

    public int clear(
            AuthenticatedUser actor,
            String statusValue,
            String ipAddress,
            String userAgent) {
        ExtractionStatus status = parseStatus(statusValue);
        // Clearing history must never remove documents owned by another member of
        // the organization, even for ADMIN/REVIEWER who can read organization-wide
        // history.
        UUID ownerFilter = actor.id();
        List<DocumentRepository.StorageKeyRecord> keys =
                documents.findStorageKeys(actor.organizationId(), ownerFilter, status);
        for (DocumentRepository.StorageKeyRecord key : keys) {
            safeDelete(key.storageKey());
        }
        auditService.record(
                actor, "DOCUMENTS_CLEARED", null, ipAddress, userAgent,
                Map.of("count", keys.size(), "status", status == null ? "ALL" : status.name()));
        return documents.deleteAll(actor.organizationId(), ownerFilter, status);
    }

    public ExtractionResponse response(ExtractionJob job) {
        return new ExtractionResponse(
                job.getId(),
                job.getStatus(),
                job.getFileName(),
                job.getMimeType(),
                job.getPageCount(),
                job.getFullText(),
                job.getPages(),
                job.getDurationMs(),
                job.getError(),
                job.getCreatedAt(),
                job.getLanguages());
    }

    private void requireAccess(ExtractionJob job, AuthenticatedUser actor) {
        if (actor == null
                || !actor.organizationId().equals(job.getOrganizationId())
                || (!actor.role().canSeeOrganizationHistory()
                    && !actor.id().equals(job.getOwnerId()))) {
            throw new DocumentException(404, "Extraction introuvable ou expiree.");
        }
    }

    private ExtractionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ExtractionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DocumentException(400, "Statut de document invalide.");
        }
    }

    private List<ExtractionStatus> parseStatuses(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<ExtractionStatus> statuses = new ArrayList<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                statuses.add(ExtractionStatus.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new DocumentException(400, "Statut de document invalide.");
            }
        }
        if (statuses.isEmpty()) {
            return null;
        }
        return statuses;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.endsWith("Z")) {
                return Instant.parse(trimmed);
            }
            if (trimmed.contains("T")) {
                return Instant.parse(trimmed);
            }
            return java.time.LocalDate.parse(trimmed).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        } catch (Exception ex) {
            throw new DocumentException(400, "Format de date invalide.");
        }
    }

    private String normalizeLanguages(String languages) {
        String value = (languages == null || languages.isBlank())
                ? properties.defaultLanguages()
                : languages;
        validateLanguages(value);
        return value;
    }

    private void validateLanguages(String languages) {
        boolean valid = Arrays.stream(languages.split("\\+", -1))
                .allMatch(properties.supportedLanguages()::contains);
        if (!valid) {
            throw new DocumentException(
                    400,
                    "Langues OCR non autorisees. Langues disponibles : "
                            + String.join(", ", properties.supportedLanguages()));
        }
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) return "document";
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (sanitized.isBlank()) return "document";

        // Keep the persisted name and storage key comfortably below the
        // database limits while preserving the extension for the UI.
        int maxLength = 180;
        if (sanitized.length() <= maxLength) return sanitized;

        int dot = sanitized.lastIndexOf('.');
        String extension = dot > 0 && sanitized.length() - dot <= 12
                ? sanitized.substring(dot)
                : "";
        String base = extension.isEmpty() ? sanitized : sanitized.substring(0, dot);
        int keep = Math.max(1, maxLength - extension.length());
        return base.substring(0, Math.min(base.length(), keep)) + extension;
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de calculer l'empreinte du fichier.", ex);
        }
    }

    private void safeDelete(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Unable to delete stored file {}: {}", storageKey, ex.getMessage());
        }
    }
}
