package dz.DocuScan.document.service;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.config.RetentionProperties;
import dz.DocuScan.document.config.StorageProperties;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.extractor.DocumentTypeDetector;
import dz.DocuScan.document.extractor.ImagePreprocessor;
import dz.DocuScan.document.extractor.OcrExtractor;
import dz.DocuScan.document.extractor.PdfTextExtractor;
import dz.DocuScan.document.extractor.PdfTextExtractorTest;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionStatus;
import dz.DocuScan.document.model.Role;
import dz.DocuScan.document.repository.DocumentRepository;
import dz.DocuScan.document.repository.JobRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class ExtractionServiceTest {

    private static final ExtractionProperties PROPS = new ExtractionProperties(
            20L * 1024 * 1024, 50, 10, 300, "fra+eng",
            List.of("fra", "eng", "ara"), "tessdata",
            1, 1000, false, 2L * 1024 * 1024, 5, 2, 4, 50);

    private static final AuthenticatedUser ACTOR = new AuthenticatedUser(
            UUID.randomUUID(), UUID.randomUUID(), "Test organization",
            "admin@test.local", "Test Admin", Role.ADMIN);

    @TempDir
    Path tempDir;

    private ExtractionResultStore store;
    private DocumentRepository documents;
    private JobRepository jobs;
    private ExtractionService service;

    @BeforeEach
    void setUp() {
        store = new CaffeineExtractionResultStore(PROPS);
        documents = Mockito.mock(DocumentRepository.class);
        jobs = Mockito.mock(JobRepository.class);
        DocumentStorage storage = new LocalDocumentStorage(new StorageProperties(
                "local", tempDir.toString(), null, null, null, null, false));
        AuditService audit = Mockito.mock(AuditService.class);
        OcrExtractor ocr = new OcrExtractor(PROPS, new ImagePreprocessor());
        PdfTextExtractor pdf = new PdfTextExtractor(PROPS, ocr);
        service = new ExtractionService(
                store, documents, jobs, storage, audit,
                new DocumentTypeDetector(), pdf, ocr, PROPS,
                new RetentionProperties(30, false));
    }

    @Test
    void rejectsEmptyFile() {
        DocumentException error = assertThrows(DocumentException.class,
                () -> service.create("empty.pdf", new byte[0], "fra", ACTOR, null, null));
        assertEquals(400, error.getStatus());
    }

    @Test
    void rejectsFileAboveMaxSize() {
        ExtractionProperties smallLimit = new ExtractionProperties(
                10, 50, 10, 300, "fra+eng", List.of("fra", "eng", "ara"), "tessdata",
                1, 1000, false, 2L * 1024 * 1024, 5, 2, 4, 50);
        ExtractionService limited = newService(smallLimit, store);

        DocumentException error = assertThrows(DocumentException.class,
                () -> limited.create("big.pdf", new byte[11], "fra", ACTOR, null, null));
        assertEquals(413, error.getStatus());
    }

    @Test
    void rejectsUnsupportedOcrLanguage() {
        DocumentException error = assertThrows(DocumentException.class,
                () -> service.create("doc.pdf", PdfTextExtractorTest.pdfWithText("text"),
                        "deu", ACTOR, null, null));
        assertEquals(400, error.getStatus());
        assertTrue(error.getMessage().contains("fra"));
    }

    @Test
    void createPersistsDocumentAndEnqueuesJob() throws Exception {
        ExtractionJob job = service.create(
                "doc.pdf", PdfTextExtractorTest.pdfWithText("text"),
                "fra+eng", ACTOR, "127.0.0.1", "JUnit");

        assertEquals(ExtractionStatus.PENDING, job.getStatus());
        assertEquals(ACTOR.organizationId(), job.getOrganizationId());
        assertEquals(ACTOR.id(), job.getOwnerId());
        assertEquals(1, job.getPageCount());
        assertEquals("fra+eng", job.getLanguages());
        assertTrue(store.findById(job.getId()).isPresent());
        verify(jobs).enqueue(any(), any(), any());
    }

    @Test
    void deleteCancelsPendingJobAndRemovesItFromStore() throws Exception {
        ExtractionJob job = service.create(
                "doc.pdf", PdfTextExtractorTest.pdfWithText("text"),
                "fra+eng", ACTOR, null, null);
        assertEquals(ExtractionStatus.PENDING, job.getStatus());

        service.delete(job.getId(), ACTOR, null, null);

        assertTrue(store.findById(job.getId()).isEmpty());
        assertTrue(job.isCancelled());
        assertEquals(ExtractionStatus.CANCELLED, job.getStatus());
    }

    @Test
    void processJobCompletesDocument() throws Exception {
        byte[] pdf = PdfTextExtractorTest.pdfWithText("completed document");
        ExtractionJob job = service.create("doc.pdf", pdf, "fra+eng", ACTOR, null, null);

        service.processJob(job, pdf, "fra+eng");

        assertEquals(ExtractionStatus.COMPLETED, job.getStatus());
        assertTrue(store.findById(job.getId()).isPresent());
    }

    @Test
    void deleteRemovesCompletedJob() throws Exception {
        byte[] pdf = PdfTextExtractorTest.pdfWithText("completed document");
        ExtractionJob job = service.create("doc.pdf", pdf, "fra+eng", ACTOR, null, null);
        service.processJob(job, pdf, "fra+eng");
        assertEquals(ExtractionStatus.COMPLETED, job.getStatus());

        service.delete(job.getId(), ACTOR, null, null);

        assertTrue(store.findById(job.getId()).isEmpty());
        assertFalse(job.isCancelled());
    }

    @Test
    void truncatesLongFileNamesWhileKeepingTheExtension() throws Exception {
        String longName = "a".repeat(300) + ".pdf";

        ExtractionJob job = service.create(
                longName, PdfTextExtractorTest.pdfWithText("text"),
                "fra", ACTOR, null, null);

        assertTrue(job.getFileName().length() <= 180);
        assertTrue(job.getFileName().endsWith(".pdf"));
    }

    @Test
    void clearOnlyRemovesDocumentsOwnedByTheActor() {
        Mockito.when(documents.findStorageKeys(
                        ACTOR.organizationId(), ACTOR.id(), ExtractionStatus.COMPLETED))
                .thenReturn(List.of());

        service.clear(ACTOR, "COMPLETED", null, null);

        verify(documents).findStorageKeys(ACTOR.organizationId(), ACTOR.id(), ExtractionStatus.COMPLETED);
        verify(documents).deleteAll(ACTOR.organizationId(), ACTOR.id(), ExtractionStatus.COMPLETED);
    }

    @Test
    void getUnknownIdReturns404() {
        DocumentException error = assertThrows(DocumentException.class,
                () -> service.get(UUID.randomUUID(), ACTOR));
        assertEquals(404, error.getStatus());
    }

    private ExtractionService newService(ExtractionProperties props, ExtractionResultStore resultStore) {
        OcrExtractor ocr = new OcrExtractor(props, new ImagePreprocessor());
        PdfTextExtractor pdf = new PdfTextExtractor(props, ocr);
        DocumentStorage storage = new LocalDocumentStorage(new StorageProperties(
                "local", tempDir.toString(), null, null, null, null, false));
        return new ExtractionService(
                resultStore, documents, jobs, storage, Mockito.mock(AuditService.class),
                new DocumentTypeDetector(), pdf, ocr, props,
                new RetentionProperties(30, false));
    }
}
