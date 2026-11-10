package dz.DocuScan.document.field;

import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.VerificationStatus;
import dz.DocuScan.document.repository.FieldRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Runs the deterministic field rules over the persisted page text and stores
 * one field per key and document.
 *
 * <p>The extraction itself is deliberately conservative: a rule must see a
 * label on the same line as the value. A detected value without a line-level
 * source span is stored as {@link VerificationStatus#UNVERIFIED}; no geometry is
 * invented and no field is created from a bare number.</p>
 *
 * <p>When local Ollama extraction is enabled, it only fills keys that the
 * deterministic rules did not produce. Ollama failures never discard the
 * deterministic result, and the AI output is subject to the same source-span
 * validation.</p>
 */
@Service
public class FieldExtractionService {

    static final String EXTRACTION_METHOD = "RULE";

    private static final Logger log = LoggerFactory.getLogger(FieldExtractionService.class);

    private final FieldRepository fields;
    private final List<FieldRule> rules;
    private final OllamaFieldExtractor aiExtractor;

    /** Test-friendly constructor: deterministic rules only. */
    public FieldExtractionService(FieldRepository fields) {
        this(fields, null);
    }

    @Autowired
    public FieldExtractionService(
            FieldRepository fields,
            OllamaFieldExtractor aiExtractor) {
        this.fields = fields;
        this.rules = List.of(new ReferenceNumberRule(), new DocumentDateRule());
        this.aiExtractor = aiExtractor;
        if (aiExtractor != null && aiExtractor.enabled()) {
            log.info("Optional Ollama field extraction is wired (missing keys only).");
        }
    }

    public void extractAndStore(ExtractionJob job) {
        if (job == null || job.getOrganizationId() == null
                || job.getPages() == null || job.getPages().isEmpty()) {
            return;
        }
        if (fields.existsForDocument(job.getOrganizationId(), job.getId())) {
            return;
        }

        List<FieldDraft> drafts = new ArrayList<>();
        for (FieldRule rule : rules) {
            List<Candidate> candidates = new ArrayList<>();
            for (PageResult page : job.getPages()) {
                for (TextLines.Line line : TextLines.of(page.text())) {
                    for (FieldMatch match : rule.matches(line.text())) {
                        candidates.add(candidate(job, page, line, match));
                    }
                }
            }
            Candidate chosen = candidates.stream()
                    .filter(candidate -> !candidate.draft().sources().isEmpty())
                    .findFirst()
                    .orElseGet(() -> candidates.stream().findFirst().orElse(null));
            if (chosen != null) {
                drafts.add(chosen.draft());
            }
        }

        Set<String> keys = drafts.stream()
                .map(FieldDraft::fieldKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        log.debug("Field extraction for document {}: pages={}, deterministicKeys={}.",
                job.getId(), job.getPages().size(), keys);
        if (aiExtractor != null) {
            try {
                for (FieldDraft draft : aiExtractor.extract(job, Set.copyOf(keys))) {
                    if (draft != null && keys.add(draft.fieldKey())) {
                        drafts.add(draft);
                    }
                }
            } catch (RuntimeException ex) {
                // Deterministic fields remain valid when the optional local
                // model is unavailable; only the exception class is logged.
                log.warn("AI field extraction failed for document {}: {}",
                        job.getId(), ex.getClass().getSimpleName());
            }
        }

        if (!drafts.isEmpty()) {
            fields.insertAll(job, drafts);
        }
    }

    private Candidate candidate(
            ExtractionJob job,
            PageResult page,
            TextLines.Line line,
            FieldMatch match) {
        int pageStart = line.start() + match.matchStart();
        int pageEnd = line.start() + match.matchEnd();
        int valueLength = Math.max(0, pageEnd - pageStart);
        PageSpan span = findSpan(page, pageStart, valueLength, match.rawValue());

        VerificationStatus status = span == null
                ? VerificationStatus.UNVERIFIED
                : match.verificationStatus();
        Double confidence = page.confidence() == null
                ? null
                : Math.max(0.0, Math.min(1.0, page.confidence()));

        List<FieldDraft.FieldSourceDraft> sources = span == null
                ? List.of()
                : List.of(new FieldDraft.FieldSourceDraft(
                        page.number(),
                        span.bboxX0(),
                        span.bboxY0(),
                        span.bboxX1(),
                        span.bboxY1(),
                        span.sourceSpace(),
                        span.pageWidth(),
                        span.pageHeight(),
                        span.rotationDeg(),
                        pageStart,
                        pageEnd,
                        span.text(),
                        0));

        return new Candidate(new FieldDraft(
                match.fieldKey(),
                match.label(),
                match.fieldType(),
                match.rawValue(),
                match.normalizedValue(),
                confidence,
                EXTRACTION_METHOD,
                extractorVersion(match.fieldKey()),
                status,
                sources));
    }

    private String extractorVersion(String fieldKey) {
        return rules.stream()
                .filter(rule -> rule.fieldKey().equals(fieldKey))
                .map(FieldRule::extractorVersion)
                .findFirst()
                .orElse("rules-v1");
    }

    private PageSpan findSpan(PageResult page, int start, int length, String rawValue) {
        int end = start + Math.max(1, length);
        List<PageSpan> spans = page.spans();
        if (spans == null || spans.isEmpty()) return null;

        return spans.stream()
                .filter(span -> span.charStart() <= start && end <= span.charEnd())
                .findFirst()
                .orElseGet(() -> spans.stream()
                        .filter(span -> span.charStart() < end && start < span.charEnd())
                        .findFirst()
                        .orElseGet(() -> spans.stream()
                                .filter(span -> span.text().contains(rawValue))
                                .findFirst()
                                .orElse(null)));
    }

    private record Candidate(FieldDraft draft) {
    }
}
