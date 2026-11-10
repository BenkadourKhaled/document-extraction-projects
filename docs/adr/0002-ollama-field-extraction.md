# ADR 0002 — Optional local Ollama field extraction

- Status: accepted
- Date: 2026-11-09
- Scope: `DocuScan-AI` branch, complementary AI extraction for traceable fields

## Context

ADR 0001 introduced deterministic `reference_number` and `document_date`
extraction with verifiable line spans, an append-only correction history and a
strict "no invented value" rule. The next step is to complement these rules with
a local generative model without weakening traceability, introducing a cloud
dependency or making tests depend on a model server.

Ollama is already installed on the target workstation and exposes a local HTTP
API. The default model is `llama3.2:1b` (about 1.3 GB), which fits the
on-premise constraint and runs without a GPU. A cold first call takes roughly
26 seconds; warm calls take roughly 4 seconds on the development host.

## Decisions

### 1. Opt-in and deterministic first

Ollama extraction is disabled by default (`app.ai.ollama.enabled=false`,
environment variable `OLLAMA_ENABLED`). The deterministic rules always run
first and their keys are never sent to the model. Ollama is asked only for
`reference_number` and `document_date` when a rule did not produce that key.
The deterministic result is authoritative and is never replaced by an AI value.

A transport failure, a timeout or an unusable model answer never fails the
document: `FieldExtractionService` catches the exception, logs only its class,
and persists the deterministic fields. Ollama is a best-effort complement, not a
processing dependency.

### 2. Minimal local transport, no SDK and no secret

`HttpOllamaClient` calls `POST {baseUrl}/api/generate` with the JDK
`HttpClient`. No vendor SDK, API key or cloud account is required. The request
uses:

- `stream=false`, `format=json`;
- `temperature=0`, bounded `num_predict` and `num_ctx`;
- a configurable `keep_alive` (default `5m`);
- a bounded input (`OLLAMA_MAX_INPUT_CHARACTERS`, default 12000) built from the
  already-extracted page text, not from the source file.

The prompt states that the document is untrusted data and must not be followed as
instructions. It asks for exactly the missing keys and forbids extra keys.

### 3. Every AI value must be found verbatim

The model answer is parsed as JSON with a small set of aliases per key (for
example `reference_number`, `reference`, `invoice_number`). A value is accepted
only when it:

1. appears **verbatim** in the page text;
2. is on a line carrying a recognized label (`facture`, `référence`, `invoice`,
   `numéro`, `رقم`, `فاتورة`, `date`, `تاريخ`, …) or is the only value on its
   line;
3. is a single line of at most 160 characters;
4. contains at least one digit;
5. for dates, parses under the existing `DocumentDateRule` semantics.

A value found in the text but not covered by a page span is stored as
`UNVERIFIED` with no source. A value absent from the text is discarded as a
hallucination. An unparsable date is discarded. An ambiguous day/month date is
stored as `AMBIGUOUS`. No bounding box is ever invented.

This guard was calibrated against the real model: on a synthetic invoice
containing only `Total: 12 500,00 DZD`, `llama3.2:1b` returned
`document_date="12 500,00 DZD"`. The value exists in the text but is not on a
recognized date label and does not parse as a date, so both checks reject it.

### 4. Persistence and traceability

Accepted AI drafts use:

- `extraction_method = OLLAMA`;
- `extractor_version = ollama-<model>` (truncated to 40 characters);
- `confidence = null` because the model does not provide a calibrated
  probability;
- the same `raw_value` / `normalized_value` / correction model as deterministic
  fields.

The worker runs field extraction through `ExtractionService.processJob`'s
post-completion hook, before the `COMPLETED` snapshot is persisted. A client that
observes `COMPLETED` can therefore read `/fields` immediately instead of racing
the model. If field extraction fails, the document result is still persisted and
field extraction remains a best-effort enrichment.

Corrections, exports, organization isolation and lineage propagation are
unchanged: AI fields go through the same repository, source and review paths.

### 5. No prompt, response or value in logs

Only the document id, the number of drafts, the exception class and the model
name/base URL at startup are logged. Prompts, model responses and extracted field
values are never logged. No secret exists to log.

### 6. Tests never require Ollama

Unit tests use a fake `OllamaClient` and a local `HttpServer` to assert the JSON
request shape and error handling. The `test` Spring profile explicitly disables
Ollama. `OllamaLiveTest` is skipped unless `OLLAMA_LIVE_TEST=true`; it validates
the real local model with a synthetic invoice.

### 7. Docker networking

The Compose API container reaches host Ollama through
`http://host.docker.internal:11434`. `docker-compose.yml` adds
`host.docker.internal:host-gateway` so the name also resolves on Linux. When
Ollama runs on the host and the API runs in Docker, Ollama must listen on
`0.0.0.0:11434` (`OLLAMA_HOST=0.0.0.0:11434`). When both run on the host,
`http://localhost:11434` is sufficient.

## Consequences

- The feature works fully offline and adds no cloud, vector store or secret.
- Deterministic behavior and existing API/TXT/JSON contracts are unchanged.
- The model can hallucinate, but the verbatim + label + span + date checks mean
  an invented value is discarded or explicitly `UNVERIFIED`, never silently
  trusted.
- Processing time increases only for documents missing a deterministic key, and
  only when the operator enables the feature.
- The model quality remains a local operational choice; changing `OLLAMA_MODEL`
  changes `extractor_version`, which keeps the extraction auditable.

## References

- [ADR 0001 — Field traceability and review workflow](0001-field-traceability.md)
- [`document-extraction-api/README.md`](../../document-extraction-api/README.md)
- [`docs/implementation-status.md`](../implementation-status.md)
