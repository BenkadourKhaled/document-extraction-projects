package dz.DocuScan.document.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.model.ExtractionJob;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight in-memory store kept for focused unit tests. Production uses
 * {@link JdbcExtractionResultStore}.
 */
public class CaffeineExtractionResultStore implements ExtractionResultStore {

    private final Cache<UUID, ExtractionJob> cache;

    public CaffeineExtractionResultStore(ExtractionProperties properties) {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtlHours(), TimeUnit.HOURS)
                .maximumSize(properties.cacheMaximumSize())
                .build();
    }

    @Override
    public void save(ExtractionJob job) {
        cache.put(job.getId(), job);
    }

    @Override
    public Optional<ExtractionJob> findById(UUID id) {
        return Optional.ofNullable(cache.getIfPresent(id));
    }

    @Override
    public boolean delete(UUID id) {
        if (cache.getIfPresent(id) == null) return false;
        cache.invalidate(id);
        return true;
    }
}
