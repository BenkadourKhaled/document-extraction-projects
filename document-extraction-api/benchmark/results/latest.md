# OCR benchmark — V1.1

Generated: 2026-10-27T09:58:01.350Z

Corpus: **synthetic internal validation** (not a customer acceptance corpus).

Engine: Tesseract 5 from Docker image `document-extraction-api`.

| Case | Language | CER | WER | Duration |
| --- | --- | ---: | ---: | ---: |
| fr-invoice | fra | 0.00 % | 0.00 % | 1237 ms |
| en-invoice | eng | 0.00 % | 0.00 % | 1420 ms |
| ar-invoice | ara | 3.61 % | 4.35 % | 1750 ms |

**Aggregate CER:** 1.34 %  
**Aggregate WER:** 1.41 %

> This corpus is synthetic and clean. It validates the measurement pipeline
> and the three Tesseract language packs; it does not prove production
> accuracy on scanned, noisy, rotated or handwritten customer documents.
