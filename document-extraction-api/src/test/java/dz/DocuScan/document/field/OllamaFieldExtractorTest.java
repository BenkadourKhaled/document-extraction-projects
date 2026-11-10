package dz.DocuScan.document.field;

import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.ai.OllamaClient;
import dz.DocuScan.document.ai.OllamaProperties;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionMethod;
import dz.DocuScan.document.model.FieldType;
import dz.DocuScan.document.model.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaFieldExtractorTest {

    private static final String TEXT = "Facture numero 2026-001\nDate: 31/01/2026";

    @Test
    void extractsOnlyMissingKeysAndVerifiesTheirSpans() {
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-001","document_date":"31/01/2026"}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        List<FieldDraft> drafts = extractor.extract(job(TEXT), Set.of());
        Map<String, FieldDraft> byKey = drafts.stream()
                .collect(Collectors.toMap(FieldDraft::fieldKey, draft -> draft));

        assertThat(byKey).containsOnlyKeys(
                ReferenceNumberRule.KEY, DocumentDateRule.KEY);

        FieldDraft reference = byKey.get(ReferenceNumberRule.KEY);
        assertThat(reference.rawValue()).isEqualTo("2026-001");
        assertThat(reference.fieldType()).isEqualTo(FieldType.IDENTIFIER);
        assertThat(reference.extractionMethod()).isEqualTo("OLLAMA");
        assertThat(reference.extractorVersion()).startsWith("ollama-");
        assertThat(reference.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(reference.sources()).hasSize(1);
        assertThat(reference.sources().get(0).charStart())
                .isEqualTo(TEXT.indexOf("2026-001"));
        assertThat(reference.sources().get(0).charEnd())
                .isEqualTo(TEXT.indexOf("2026-001") + "2026-001".length());

        FieldDraft date = byKey.get(DocumentDateRule.KEY);
        assertThat(date.rawValue()).isEqualTo("31/01/2026");
        assertThat(date.normalizedValue()).isEqualTo("2026-01-31");
        assertThat(date.fieldType()).isEqualTo(FieldType.DATE);
        assertThat(date.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(date.sources()).hasSize(1);

        assertThat(client.lastPrompt)
                .contains("reference_number")
                .contains("document_date");
    }

    @Test
    void skipsKeysAlreadyProducedByDeterministicRules() {
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-001","document_date":"31/01/2026"}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        List<FieldDraft> drafts = extractor.extract(
                job(TEXT), Set.of(ReferenceNumberRule.KEY));

        assertThat(drafts).extracting(FieldDraft::fieldKey)
                .containsExactly(DocumentDateRule.KEY);
        assertThat(client.lastPrompt).doesNotContain("reference_number");
        assertThat(client.lastPrompt).contains("document_date");
    }

    @Test
    void dropsValuesThatAreNotPresentVerbatimInTheDocument() {
        FakeClient client = new FakeClient("""
                {"reference_number":"INVENTED-999","document_date":null}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        assertThat(extractor.extract(job(TEXT), Set.of())).isEmpty();
    }

    @Test
    void rejectsAValueFromAnUnlabelledLine() {
        String text = "Facture numero 2026-001\nTotal: 2026-999";
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-999","document_date":null}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        assertThat(extractor.extract(job(text), Set.of())).isEmpty();
    }

    @Test
    void marksValueWithoutSpanAsUnverified() {
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-001","document_date":null}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        List<FieldDraft> drafts = extractor.extract(jobWithoutSpans(TEXT), Set.of());

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).verificationStatus())
                .isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(drafts.get(0).sources()).isEmpty();
    }

    @Test
    void keepsAmbiguousDateAmbiguous() {
        String text = "Facture numero 2026-001\nDate: 02/01/2026";
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-001","document_date":"02/01/2026"}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        List<FieldDraft> drafts = extractor.extract(job(text), Set.of());
        FieldDraft date = drafts.stream()
                .filter(draft -> draft.fieldKey().equals(DocumentDateRule.KEY))
                .findFirst()
                .orElseThrow();

        assertThat(date.verificationStatus()).isEqualTo(VerificationStatus.AMBIGUOUS);
        assertThat(date.sources()).hasSize(1);
    }

    @Test
    void rejectsADateThatIsNotParseable() {
        String text = "Facture numero 2026-001\nTotal: 12 500,00 DZD";
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-001","document_date":"12 500,00 DZD"}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        List<FieldDraft> drafts = extractor.extract(job(text), Set.of());

        assertThat(drafts).extracting(FieldDraft::fieldKey)
                .containsExactly(ReferenceNumberRule.KEY);
    }

    @Test
    void acceptsABareDateLine() {
        String text = "Facture numero 2026-001\n31/01/2026";
        FakeClient client = new FakeClient("""
                {"reference_number":null,"document_date":"31/01/2026"}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        List<FieldDraft> drafts = extractor.extract(job(text), Set.of());

        assertThat(drafts).extracting(FieldDraft::fieldKey)
                .containsExactly(DocumentDateRule.KEY);
        assertThat(drafts.get(0).verificationStatus())
                .isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void rejectsAReferenceWithoutDigits() {
        FakeClient client = new FakeClient("""
                {"reference_number":"Facture","document_date":null}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        assertThat(extractor.extract(job(TEXT), Set.of())).isEmpty();
    }

    @Test
    void returnsEmptyWhenDisabled() {
        FakeClient client = new FakeClient("""
                {"reference_number":"2026-001","document_date":"31/01/2026"}
                """);
        OllamaFieldExtractor extractor = extractor(client, false, 12_000);

        assertThat(extractor.extract(job(TEXT), Set.of())).isEmpty();
        assertThat(client.calls).isZero();
    }

    @Test
    void returnsEmptyOnInvalidJson() {
        FakeClient client = new FakeClient("not json");
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        assertThat(extractor.extract(job(TEXT), Set.of())).isEmpty();
    }

    @Test
    void acceptsMarkdownFencesAndAliases() {
        FakeClient client = new FakeClient("""
                ```json
                {"reference":"2026-001","date":"31/01/2026"}
                ```
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 12_000);

        assertThat(extractor.extract(job(TEXT), Set.of())).hasSize(2);
    }

    @Test
    void truncatesLongInputBeforeCallingTheModel() {
        FakeClient client = new FakeClient("""
                {"reference_number":null,"document_date":null}
                """);
        OllamaFieldExtractor extractor = extractor(client, true, 1_000);
        String longText = "Facture numero 2026-001\n" + "x".repeat(5_000);

        extractor.extract(job(longText), Set.of());

        assertThat(client.lastPrompt.length()).isLessThan(2_000);
    }

    private static OllamaFieldExtractor extractor(
            FakeClient client, boolean enabled, int maxInputCharacters) {
        return new OllamaFieldExtractor(
                client,
                new OllamaProperties(
                        enabled,
                        "http://localhost:11434",
                        "llama3.2:1b",
                        5,
                        maxInputCharacters,
                        64,
                        2_048,
                        "5m"),
                new ObjectMapper());
    }

    private static ExtractionJob job(String text) {
        return job(text, true);
    }

    private static ExtractionJob jobWithoutSpans(String text) {
        return job(text, false);
    }

    private static ExtractionJob job(String text, boolean withSpans) {
        UUID id = UUID.randomUUID();
        ExtractionJob job = new ExtractionJob(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "invoice.pdf",
                "application/pdf",
                100L,
                "sha",
                "storage-key",
                "fra+eng",
                Instant.now(),
                null);

        int split = text.indexOf('\n');
        List<PageSpan> spans;
        if (!withSpans) {
            spans = List.of();
        } else if (split < 0) {
            spans = List.of(new PageSpan(
                    text, 0, text.length(),
                    0.05, 0.10, 0.80, 0.14, "PDF_POINTS", 612, 792, 0, null));
        } else {
            spans = List.of(
                    new PageSpan(
                            text.substring(0, split), 0, split,
                            0.05, 0.10, 0.80, 0.14, "PDF_POINTS", 612, 792, 0, null),
                    new PageSpan(
                            text.substring(split + 1), split + 1, text.length(),
                            0.05, 0.20, 0.60, 0.24, "PDF_POINTS", 612, 792, 0, null));
        }

        job.complete(
                List.of(new PageResult(1, text, ExtractionMethod.NATIVE, null, spans)),
                text,
                1L);
        return job;
    }

    private static final class FakeClient implements OllamaClient {

        private final String response;
        private String lastPrompt;
        private int calls;

        private FakeClient(String response) {
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            calls++;
            lastPrompt = prompt;
            return response;
        }
    }
}
