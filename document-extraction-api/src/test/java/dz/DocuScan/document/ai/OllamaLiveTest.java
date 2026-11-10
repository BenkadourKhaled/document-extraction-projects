package dz.DocuScan.document.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.field.DocumentDateRule;
import dz.DocuScan.document.field.FieldDraft;
import dz.DocuScan.document.field.OllamaFieldExtractor;
import dz.DocuScan.document.field.ReferenceNumberRule;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.ExtractionMethod;
import dz.DocuScan.document.model.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in smoke test against the real local Ollama instance.
 *
 * <p>Run manually with:
 * {@code OLLAMA_LIVE_TEST=true mvn -Dtest=OllamaLiveTest test}.
 * It is skipped by default so CI never needs a model.</p>
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_TEST", matches = "(?i)true")
class OllamaLiveTest {

    @Test
    void extractsAReferenceAndDateFromSyntheticText() {
        String baseUrl = environment("OLLAMA_BASE_URL", "http://localhost:11434");
        String model = environment("OLLAMA_MODEL", "llama3.2:1b");
        OllamaProperties properties = new OllamaProperties(
                true, baseUrl, model, 120, 12_000, 128, 4_096, "5m");
        ObjectMapper mapper = new ObjectMapper();
        OllamaFieldExtractor extractor = new OllamaFieldExtractor(
                new HttpOllamaClient(properties, mapper), properties, mapper);

        String text = "Facture numero 2026-001\nDate: 31/01/2026";
        List<FieldDraft> drafts = extractor.extract(job(text), Set.of());

        assertThat(drafts).allSatisfy(draft -> {
            assertThat(text).contains(draft.rawValue());
            if (draft.verificationStatus() == VerificationStatus.VERIFIED) {
                assertThat(draft.sources()).isNotEmpty();
            }
        });
        assertThat(drafts.stream().map(FieldDraft::fieldKey).collect(Collectors.toSet()))
                .contains(ReferenceNumberRule.KEY, DocumentDateRule.KEY);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static ExtractionJob job(String text) {
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
        List<PageSpan> spans = List.of(
                new PageSpan(
                        text.substring(0, split), 0, split,
                        0.05, 0.10, 0.80, 0.14, "PDF_POINTS", 612, 792, 0, null),
                new PageSpan(
                        text.substring(split + 1), split + 1, text.length(),
                        0.05, 0.20, 0.60, 0.24, "PDF_POINTS", 612, 792, 0, null));
        job.complete(
                List.of(new PageResult(1, text, ExtractionMethod.NATIVE, null, spans)),
                text,
                1L);
        return job;
    }
}
