package dz.DocuScan.document.service;

import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.repository.DocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExtractionResultStore implements ExtractionResultStore {

    private final DocumentRepository documents;

    public JdbcExtractionResultStore(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public void save(ExtractionJob job) {
        boolean updated = documents.update(job);
        if (!updated && job.getStatus() == ExtractionStatus.PENDING) {
            // The document may have been deleted while the worker was running:
            // never resurrect it from a late save.
            documents.insert(job);
        }
    }

    @Override
    public Optional<ExtractionJob> findById(UUID id) {
        return documents.findById(id);
    }

    @Override
    public boolean delete(UUID id) {
        return documents.delete(id);
    }
}
