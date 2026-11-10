# ADR 0001 — Field traceability and review workflow

- Status: accepted
- Date: 2026-11-08
- Scope: `DocuScan-AI` branch, first deterministic traceability slice

## Context

DocuScan currently persists a document-level `full_text` and a `pages_json`
snapshot. Extraction methods (`NATIVE`, `OCR`) produce text but discard the
position of each line. Reviewers can edit the displayed text locally, but there
is no durable field, source, correction or audit trail.

The next iteration must make every structured value traceable to a page and a
source region, without changing the existing extraction architecture, without
adding an LLM, and without breaking the H2 test profile or existing TXT/JSON
clients.

## Decisions

### 1. Scope: deterministic extraction only

The first vertical slice extracts two fields with rules:

- `reference_number`;
- `document_date`.

They are marked `RULE`. The deterministic rules are always evaluated first. An
optional local AI extractor (see ADR 0002) may later fill keys the rules did not
produce. Any AI extractor must return a source span; a value without a verifiable
span is stored as `UNVERIFIED`, never silently accepted.

> Note: the existing OCR path already uses **Tesseract 5**, an LSTM-based OCR
> engine. It is an AI/ML component but not a generative model or LLM. ADR 0002
> adds an opt-in local Ollama extractor for missing fields; it remains disabled by
> default and introduces no cloud call, secret or vector store.

### 2. Coordinates are normalized and rotation-aware

For every source span DocuScan stores:

- `bbox_x0`, `bbox_y0`, `bbox_x1`, `bbox_y1` normalized to `[0, 1]`;
- top-left origin of the page **as displayed after rotation**;
- `source_space` (`PDF_POINTS`, `IMAGE_PIXELS` or `OCR_PIXELS`);
- the source page width and height in that source space;
- the page rotation in degrees;
- `char_start` / `char_end` relative to the stored page text.

The frontend only uses normalized boxes: `left = x0 * 100%`, etc. It never
reconstructs a box from raw PDF/OCR pixels.

### 3. Spans are line-level in this slice

Both PDFBox `TextPosition` and Tesseract `Word` data are captured at line level.
This is precise enough to highlight the line containing a field and avoids
inventing character geometry. A later iteration can split line spans into
word/character spans without changing the API contract.

Tesseract reads a preprocessed image (resize, deskew). The preprocessor records
the applied scale and rotation and maps each OCR box back to the original image
before normalization. A bbox computed on the preprocessed image is never stored
as if it belonged to the source image.

### 4. Re-extraction uses a stable lineage

`reprocess` still creates a new document (the existing behaviour and API remain
unchanged). A new nullable-then-backfilled column `documents.lineage_id` links
all documents produced from the same source. `reprocess` copies the lineage and
records `source_document_id`.

Fields keep a stable `field_key`. Effective values and correction history are
resolved across the lineage for the same organization and field key. Therefore
a correction made before reprocessing remains effective after reprocessing,
while each extraction run still keeps its own raw value and source spans. This
is the explicit Option B (controlled lineage) from the design review; it avoids
a risky rewrite of the document identity model while keeping corrections
observable and testable.

### 5. Corrections are append-only

`field_corrections` rows are never updated in place except to set
`superseded_at`. The extracted `raw_value` is immutable.

```
effective_value = latest current correction in the lineage
                  if one exists
                  else extracted raw value
```

The effective value is computed in one service method used by the field API,
the TXT export and the JSON export. Corrections never rewrite `raw_value`, so
Arabic-Indic/Persian digits and the original logical order remain available even
after review. A partial PostgreSQL index is not used
because H2 must stay compatible: a nullable `active_marker` column plus a unique
index on `(field_id, active_marker)` gives the same guarantee on both databases
(multiple `NULL` values are allowed, at most one active row per field). The
lineage-wide "one current correction per field key" rule is enforced inside a
transaction that supersedes the previous current row before inserting.

### 6. Export compatibility

JSON exports keep every existing key and add `schema_version` and `fields`.
Each field contains `raw_value`, `normalized_value`, `effective_value`,
`verification_status`, `confidence`, `extraction_method`, `extractor_version`
and `sources`.

TXT exports keep the full extracted text first, then append a clearly delimited
`Extracted fields (effective values)` section. Existing consumers can continue
to read the text prefix; new consumers can parse the field section. Both output
formats are UTF-8 without BOM. Arabic-Indic/Persian digits, mixed-direction
strings and logical character order are preserved exactly.

Both export paths (`ExtractionController` for one document and
`DocumentController` for the ZIP) call the same export service.

### 7. Arabic, English and bidi

The stored `raw_value` is the exact text detected in the source after removing
only invisible control characters. `normalized_value` is NFC and folds
Arabic-Indic/Persian digits to ASCII for comparison and search. Digit folding is
never used for display or export. `dir="auto"` isolates mixed values in the UI.
The server never inserts Unicode bidi control characters.

Date parsing marks a value `AMBIGUOUS` when the day/month order or the calendar
(Gregorian vs Hijri) cannot be proven. DocuScan never guesses.

### 8. No pgvector in this iteration

The schema contains no vector column and no pgvector extension. No embedding call
is made. The field model, the append-only correction history and the deterministic
rules are ready for an optional PostgreSQL migration and a separate test profile
if semantic search becomes a real requirement.

### 9. AI extension point

`FieldRule` remains the deterministic extension point. The optional AI extractor
implements the same persistence contract and returns a `FieldDraft` with the
exact `rawValue` observed in the line, plus a verification status.
`FieldExtractionService` maps that value to an existing page span; if no span
covers it, the field is persisted as `UNVERIFIED` and is never given invented
geometry. The AI layer therefore runs on the already-extracted page text/spans
and may not bypass the append-only correction or organization-isolation rules.
The implemented `OllamaFieldExtractor` follows these rules and is documented in
[ADR 0002](0002-ollama-field-extraction.md); it is opt-in, disabled by default,
and requires no secret or vector store.

## Rollback

The migration is additive: `V3__field_traceability.sql` creates three new tables
and adds two columns to `documents`. Rolling back the feature can drop the new
tables and columns; existing `full_text`, `pages_json`, jobs, audit events and
exports remain valid. No existing column is rewritten.

## Consequences

- Every returned field either has at least one source or is explicitly
  `UNVERIFIED`.
- Corrections survive reprocessing through the lineage rule.
- The `pages_json` payload grows because it now contains spans. This is accepted
  for the current 50-page limit and can be optimized later with a dedicated
  table if needed.
- Line-level highlights are coarser than word-level highlights. This is a
  documented limitation of the first slice, not a hidden approximation.
