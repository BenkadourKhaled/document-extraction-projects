package dz.DocuScan.document.field;

import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.model.ExtractionJob;
import dz.DocuScan.document.model.VerificationStatus;
import dz.DocuScan.document.repository.FieldRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the deterministic field rules over the persisted page text and stores
 * one field per key and document.
 *
 * <p>The extraction itself is deliberately conservative: a rule must see a
 * label on the same line as the value. A detected value without a line-level
 * source span is stored as {@link VerificationStatus#UNVERIFIED}; no geometry is
 * invented and no field is created from a bare number.</p>
 */
@Service
public class FieldExtractionService {

    static final String EXTRACTION_METHOD = "RULE";

    private final FieldRepository fields;
    private final List<FieldRule> rules;

    public FieldExtractionService(FieldRepository fields) {
        this.fields = fields;
        this.rules = List.of(new ReferenceNumberRule(), new DocumentDateRule());
    }

    @Transactional
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
                for (Line line : lines(page.text())) {
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

        if (!drafts.isEmpty()) {
            fields.insertAll(job, drafts);
        }
    }

    private Candidate candidate(
            ExtractionJob job,
            PageResult page,
            Line line,
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

    static List<Line> lines(String text) {
        List<Line> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        int start = 0;
        while (start <= text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            String value = text.substring(start, end);
            int cleanStart = start;
            int cleanEnd = end;
            while (cleanStart < cleanEnd && (text.charAt(cleanStart) == '\r'
                    || Character.isWhitespace(text.charAt(cleanStart)))) {
                cleanStart++;
            }
            while (cleanEnd > cleanStart && (text.charAt(cleanEnd - 1) == '\r'
                    || Character.isWhitespace(text.charAt(cleanEnd - 1)))) {
                cleanEnd--;
            }
            lines.add(new Line(cleanStart, cleanEnd, text.substring(cleanStart, cleanEnd)));
            if (newline < 0) break;
            start = newline + 1;
        }
        return lines;
    }

    record Line(int start, int end, String text) {
    }

    private record Candidate(FieldDraft draft) {
    }
}
