# OCR benchmark V1.1

This benchmark measures Tesseract OCR quality for the three supported
languages: French (`fra`), English (`eng`) and Arabic (`ara`). It uses the same
Tesseract installation and language data as the production API image. The
benchmark itself invokes only Tesseract: it does **not** exercise the optional
Ollama field extractor and uses no external OCR service.

## Corpus

The current corpus is **synthetic** and generated with Windows GDI+:

- `corpus/fr-invoice.png`
- `corpus/en-invoice.png`
- `corpus/ar-invoice.png`
- matching UTF-8 ground-truth files
- `corpus/manifest.json`

It is deliberately clean and high-contrast. It verifies the benchmark pipeline
and the three language packs; it is **not** a customer acceptance corpus.

To regenerate the images on Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\benchmark\generate-corpus.ps1
```

## Run

The API Docker image must be built first:

```powershell
docker compose build document-extraction-api
node .\benchmark\run.mjs
```

The script invokes:

```text
docker compose run --rm --no-deps \
  -v <corpus>:/corpus:ro \
  --entrypoint tesseract \
  document-extraction-api /corpus/<image> stdout -l <lang> --psm 6
```

It writes:

- `results/latest.json`: machine-readable results and methodology
- `results/latest.md`: human-readable report
- `results/raw/<case>.txt`: raw Tesseract output

Metrics:

- **CER**: character error rate using Levenshtein distance
- **WER**: word error rate using Levenshtein distance
- text is normalized to NFC with whitespace collapsed

An optional quality gate is available:

```powershell
$env:BENCHMARK_MAX_CER = "0.10"
node .\benchmark\run.mjs
```

The command exits with a non-zero status if the aggregate CER exceeds the gate.

## Limitations

- Synthetic, clean documents only.
- No scanned noise, rotation, shadows, stamps or handwriting.
- Arabic handwriting and complex tables are out of scope for this corpus.
- A real acceptance benchmark requires a representative anonymized FR/EN/AR
  customer corpus, approved for internal testing.
