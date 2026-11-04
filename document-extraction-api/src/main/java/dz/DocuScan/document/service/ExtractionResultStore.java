package dz.DocuScan.document.service;
import dz.DocuScan.document.model.ExtractionJob;
import java.util.Optional;
import java.util.UUID;
public interface ExtractionResultStore { void save(ExtractionJob job); Optional<ExtractionJob> findById(UUID id); boolean delete(UUID id); }
