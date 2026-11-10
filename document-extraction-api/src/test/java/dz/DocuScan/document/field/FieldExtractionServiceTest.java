package dz.DocuScan.document.field;

import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionMethod;
import dz.DocuScan.document.model.FieldType;
import dz.DocuScan.document.model.VerificationStatus;
import dz.DocuScan.document.repository.FieldRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldExtractionServiceTest {

    private static final UUID ORG = UUID.randomUUID();

    @Test
    void extractsReferenceAndDateWithSourceSpans() {
        FieldRepository repository = mock(FieldRepository.class);
        UUID documentId = UUID.randomUUID();
        when(repository.existsForDocument(ORG, documentId)).thenReturn(false);
        FieldExtractionService service = new FieldExtractionService(repository);

        String text = "Facture numero 2026-001\nDate: 31/01/2026";
        int dateStart = text.indexOf("Date:");
        List<PageSpan> spans = List.of(
                new PageSpan("Facture numero 2026-001", 0, dateStart - 1,
                        0.05, 0.10, 0.60, 0.14, "PDF_POINTS", 612, 792, 0, null),
                new PageSpan("Date: 31/01/2026", dateStart, text.length(),
                        0.05, 0.20, 0.35, 0.24, "PDF_POINTS", 612, 792, 0, null));
        ExtractionJob job = job(documentId, List.of(
                new PageResult(1, text, ExtractionMethod.NATIVE, null, spans)));

        service.extractAndStore(job);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(eq(job), captor.capture());
        Map<String, FieldDraft> drafts = captor.getValue().stream()
                .collect(Collectors.toMap(FieldDraft::fieldKey, draft -> draft));

        FieldDraft reference = drafts.get(ReferenceNumberRule.KEY);
        assertEquals("2026-001", reference.rawValue());
        assertEquals(VerificationStatus.VERIFIED, reference.verificationStatus());
        assertEquals(1, reference.sources().size());
        assertEquals(15, reference.sources().get(0).charStart());
        assertEquals(23, reference.sources().get(0).charEnd());
        assertEquals(0.05, reference.sources().get(0).bboxX0(), 0.001);
        assertEquals(1, reference.sources().get(0).pageNumber());

        FieldDraft date = drafts.get(DocumentDateRule.KEY);
        assertEquals("31/01/2026", date.rawValue());
        assertEquals("2026-01-31", date.normalizedValue());
        assertEquals(VerificationStatus.VERIFIED, date.verificationStatus());
        assertEquals(1, date.sources().size());
    }

    @Test
    void marksDetectedFieldsWithoutSourceAsUnverified() {
        FieldRepository repository = mock(FieldRepository.class);
        UUID documentId = UUID.randomUUID();
        when(repository.existsForDocument(ORG, documentId)).thenReturn(false);
        FieldExtractionService service = new FieldExtractionService(repository);

        String text = "Facture numero 2026-001\nDate: 31/01/2026";
        ExtractionJob job = job(documentId, List.of(
                new PageResult(1, text, ExtractionMethod.NATIVE, null, List.of())));

        service.extractAndStore(job);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(eq(job), captor.capture());
        assertTrue(captor.getValue().stream().allMatch(draft ->
                draft.verificationStatus() == VerificationStatus.UNVERIFIED
                        && draft.sources().isEmpty()));
    }

    @Test
    void neverCreatesFieldsFromUnlabelledNumbers() {
        FieldRepository repository = mock(FieldRepository.class);
        UUID documentId = UUID.randomUUID();
        when(repository.existsForDocument(ORG, documentId)).thenReturn(false);
        FieldExtractionService service = new FieldExtractionService(repository);

        ExtractionJob job = job(documentId, List.of(new PageResult(
                1, "Total: 12 500,00 DZD", ExtractionMethod.NATIVE, null, List.of())));

        service.extractAndStore(job);

        verify(repository, never()).insertAll(any(), any());
    }

    @Test
    void doesNotDuplicateFieldsWhenExtractionIsRetried() {
        FieldRepository repository = mock(FieldRepository.class);
        UUID documentId = UUID.randomUUID();
        when(repository.existsForDocument(ORG, documentId)).thenReturn(true);
        FieldExtractionService service = new FieldExtractionService(repository);

        service.extractAndStore(job(documentId, List.of(new PageResult(
                1, "Facture numero 2026-001", ExtractionMethod.NATIVE, null, List.of()))));

        verify(repository, never()).insertAll(any(), any());
    }

    @Test
    void mergesAiFieldsOnlyForKeysMissingFromDeterministicRules() {
        FieldRepository repository = mock(FieldRepository.class);
        UUID documentId = UUID.randomUUID();
        when(repository.existsForDocument(ORG, documentId)).thenReturn(false);
        OllamaFieldExtractor ai = mock(OllamaFieldExtractor.class);

        ExtractionJob job = job(documentId, List.of(new PageResult(
                1, "Facture numero 2026-001", ExtractionMethod.NATIVE, null, List.of())));
        FieldDraft aiDate = new FieldDraft(
                DocumentDateRule.KEY,
                DocumentDateRule.LABEL,
                FieldType.DATE,
                "31/01/2026",
                "2026-01-31",
                null,
                "OLLAMA",
                "ollama-llama3.2:1b",
                VerificationStatus.UNVERIFIED,
                List.of());
        when(ai.extract(eq(job), anySet())).thenReturn(List.of(aiDate));

        new FieldExtractionService(repository, ai).extractAndStore(job);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(eq(job), captor.capture());
        Map<String, FieldDraft> drafts = captor.getValue().stream()
                .collect(Collectors.toMap(FieldDraft::fieldKey, draft -> draft));

        assertEquals(2, drafts.size());
        assertEquals(FieldExtractionService.EXTRACTION_METHOD,
                drafts.get(ReferenceNumberRule.KEY).extractionMethod());
        assertEquals("OLLAMA", drafts.get(DocumentDateRule.KEY).extractionMethod());
        verify(ai).extract(eq(job), eq(Set.of(ReferenceNumberRule.KEY)));
    }

    @Test
    void keepsDeterministicFieldsWhenAiFails() {
        FieldRepository repository = mock(FieldRepository.class);
        UUID documentId = UUID.randomUUID();
        when(repository.existsForDocument(ORG, documentId)).thenReturn(false);
        OllamaFieldExtractor ai = mock(OllamaFieldExtractor.class);

        ExtractionJob job = job(documentId, List.of(new PageResult(
                1, "Facture numero 2026-001", ExtractionMethod.NATIVE, null, List.of())));
        when(ai.extract(eq(job), anySet()))
                .thenThrow(new IllegalStateException("ollama unavailable"));

        new FieldExtractionService(repository, ai).extractAndStore(job);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(eq(job), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(ReferenceNumberRule.KEY, captor.getValue().get(0).fieldKey());
        assertEquals(FieldExtractionService.EXTRACTION_METHOD,
                captor.getValue().get(0).extractionMethod());
    }

    private ExtractionJob job(UUID documentId, List<PageResult> pages) {
        ExtractionJob job = new ExtractionJob(
                documentId, ORG, UUID.randomUUID(), "invoice.pdf", "application/pdf",
                100L, "sha", "storage-key", "fra+eng", Instant.now(), null);
        String text = pages.stream().map(PageResult::text).collect(Collectors.joining("\n\n"));
        job.complete(pages, text, 1L);
        return job;
    }
}
