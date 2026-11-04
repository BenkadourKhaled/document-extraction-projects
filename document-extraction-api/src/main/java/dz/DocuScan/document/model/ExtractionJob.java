package dz.DocuScan.document.model;

import dz.DocuScan.document.dto.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ExtractionJob {
    private final UUID id;
    private final UUID organizationId;
    private final UUID ownerId;
    private final String fileName;
    private final String mimeType;
    private final long sizeBytes;
    private final String sha256;
    private final String storageKey;
    private final String languages;
    private final Instant createdAt;
    private final Instant retentionUntil;

    private volatile ExtractionStatus status = ExtractionStatus.PENDING;
    private volatile int pageCount;
    private volatile String fullText = "";
    private volatile List<PageResult> pages = List.of();
    private volatile long durationMs;
    private volatile String error;
    private volatile Instant completedAt;
    private volatile boolean cancelled;

    public ExtractionJob(
            UUID id,
            UUID organizationId,
            UUID ownerId,
            String fileName,
            String mimeType,
            long sizeBytes,
            String sha256,
            String storageKey,
            String languages,
            Instant createdAt,
            Instant retentionUntil) {
        this.id = id;
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.languages = languages;
        this.createdAt = createdAt;
        this.retentionUntil = retentionUntil;
    }

    /** Convenience constructor used by legacy unit tests that do not persist the job. */
    public ExtractionJob(UUID id, String fileName, String mimeType) {
        this(id, null, null, fileName, mimeType, 0L, "", "", "fra+eng",
                Instant.now(), null);
    }

    public synchronized void setPageCount(int pageCount) {
        this.pageCount = Math.max(0, pageCount);
    }

    public synchronized void start() {
        if (cancelled) return;
        status = ExtractionStatus.PROCESSING;
        error = null;
    }

    public synchronized void complete(List<PageResult> results, String text, long duration) {
        if (cancelled) return;
        pages = List.copyOf(results);
        pageCount = results.size();
        fullText = text == null ? "" : text;
        durationMs = duration;
        status = ExtractionStatus.COMPLETED;
        completedAt = Instant.now();
        error = null;
    }

    public synchronized void fail(String message, long duration) {
        if (cancelled) return;
        error = message;
        durationMs = duration;
        status = ExtractionStatus.FAILED;
        completedAt = Instant.now();
    }

    public synchronized void cancel(String message) {
        cancelled = true;
        error = message;
        status = ExtractionStatus.CANCELLED;
        completedAt = Instant.now();
    }

    /** Restores a persisted snapshot without changing the job identity. */
    public synchronized void restore(
            ExtractionStatus restoredStatus,
            int restoredPageCount,
            String restoredFullText,
            List<PageResult> restoredPages,
            long restoredDurationMs,
            String restoredError,
            Instant restoredCompletedAt) {
        this.status = restoredStatus;
        this.pageCount = restoredPageCount;
        this.fullText = restoredFullText == null ? "" : restoredFullText;
        this.pages = restoredPages == null ? List.of() : List.copyOf(restoredPages);
        this.durationMs = restoredDurationMs;
        this.error = restoredError;
        this.completedAt = restoredCompletedAt;
        this.cancelled = restoredStatus == ExtractionStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public String getLanguages() { return languages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRetentionUntil() { return retentionUntil; }
    public ExtractionStatus getStatus() { return status; }
    public int getPageCount() { return pageCount; }
    public String getFullText() { return fullText; }
    public List<PageResult> getPages() { return pages; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
    public Instant getCompletedAt() { return completedAt; }
    public boolean isCancelled() { return cancelled || status == ExtractionStatus.CANCELLED; }
}
