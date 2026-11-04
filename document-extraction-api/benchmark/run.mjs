#!/usr/bin/env node
/**
 * OCR benchmark for the document extraction API.
 *
 * Runs Tesseract from the API Docker image on the corpus declared in
 * benchmark/corpus/manifest.json, compares the output with the ground truth,
 * and writes CER/WER results. No external OCR service, LLM or vision model is
 * used.
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(here, '..');
const corpusDir = path.resolve(here, process.env.BENCHMARK_CORPUS ?? 'corpus');
const resultsDir = path.resolve(here, process.env.BENCHMARK_RESULTS ?? 'results');
const service = process.env.BENCHMARK_SERVICE ?? 'document-extraction-api';
const maxCer = process.env.BENCHMARK_MAX_CER
  ? Number.parseFloat(process.env.BENCHMARK_MAX_CER)
  : null;

function normalize(text) {
  return text
    .replace(/^\uFEFF/, '')
    .replace(/\r\n?/g, '\n')
    .normalize('NFC')
    .replace(/\s+/g, ' ')
    .trim();
}

function editDistance(left, right) {
  const a = Array.from(left);
  const b = Array.from(right);
  let previous = Array.from({ length: b.length + 1 }, (_, index) => index);
  for (let i = 1; i <= a.length; i += 1) {
    const current = [i];
    for (let j = 1; j <= b.length; j += 1) {
      const substitution = previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1);
      current[j] = Math.min(previous[j] + 1, current[j - 1] + 1, substitution);
    }
    previous = current;
  }
  return previous[b.length];
}

function rate(expected, actual) {
  if (expected.length === 0) return actual.length === 0 ? 0 : 1;
  return editDistance(expected, actual) / expected.length;
}

function runTesseract(image, language, psm) {
  const mount = `${corpusDir}:/corpus:ro`;
  const started = Date.now();
  const result = spawnSync(
    'docker',
    [
      'compose',
      'run',
      '--rm',
      '--no-deps',
      '-v',
      mount,
      '--entrypoint',
      'tesseract',
      service,
      `/corpus/${image}`,
      'stdout',
      '-l',
      language,
      '--psm',
      String(psm),
    ],
    {
      cwd: projectRoot,
      encoding: 'utf8',
      maxBuffer: 20 * 1024 * 1024,
    },
  );
  const durationMs = Date.now() - started;

  if (result.error) {
    throw new Error(`Unable to start Docker: ${result.error.message}`);
  }
  if (result.status !== 0) {
    throw new Error(
      `Tesseract failed for ${image} (exit ${result.status}).\n${result.stderr ?? ''}`,
    );
  }
  return { text: result.stdout.replace(/\r\n?/g, '\n').trimEnd(), durationMs };
}

function markdownReport(result) {
  const rows = result.cases
    .map(
      (item) =>
        `| ${item.id} | ${item.language} | ${item.cerPercent.toFixed(2)} % | ` +
        `${item.werPercent.toFixed(2)} % | ${item.durationMs} ms |`,
    )
    .join('\n');

  return `# OCR benchmark — V1.1\n\n` +
    `Generated: ${result.generatedAt}\n\n` +
    `Corpus: **synthetic internal validation** (not a customer acceptance corpus).\n\n` +
    `Engine: Tesseract 5 from Docker image \`${result.service}\`.\n\n` +
    `| Case | Language | CER | WER | Duration |\n` +
    `| --- | --- | ---: | ---: | ---: |\n` +
    `${rows}\n\n` +
    `**Aggregate CER:** ${result.aggregate.cerPercent.toFixed(2)} %  \n` +
    `**Aggregate WER:** ${result.aggregate.werPercent.toFixed(2)} %\n\n` +
    `> This corpus is synthetic and clean. It validates the measurement pipeline\n` +
    `> and the three Tesseract language packs; it does not prove production\n` +
    `> accuracy on scanned, noisy, rotated or handwritten customer documents.\n`;
}

async function main() {
  const manifestPath = path.join(corpusDir, 'manifest.json');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  if (!Array.isArray(manifest) || manifest.length === 0) {
    throw new Error(`No benchmark cases in ${manifestPath}`);
  }

  const cases = [];
  for (const entry of manifest) {
    const expectedText = normalize(
      await readFile(path.join(corpusDir, entry.groundTruth), 'utf8'),
    );
    const { text, durationMs } = runTesseract(entry.file, entry.language, entry.psm ?? 6);
    const actualText = normalize(text);
    const expectedWords = expectedText ? expectedText.split(' ') : [];
    const actualWords = actualText ? actualText.split(' ') : [];
    const cer = rate(expectedText, actualText);
    const wer = rate(expectedWords, actualWords);

    const rawDir = path.join(resultsDir, 'raw');
    await mkdir(rawDir, { recursive: true });
    await writeFile(path.join(rawDir, `${entry.id}.txt`), `${actualText}\n`, 'utf8');

    cases.push({
      id: entry.id,
      file: entry.file,
      language: entry.language,
      psm: entry.psm ?? 6,
      expectedCharacters: expectedText.length,
      actualCharacters: actualText.length,
      expectedWords: expectedWords.length,
      actualWords: actualWords.length,
      cer,
      wer,
      cerPercent: cer * 100,
      werPercent: wer * 100,
      durationMs,
    });
    process.stdout.write(
      `${entry.id.padEnd(12)} CER ${(cer * 100).toFixed(2).padStart(6)} %  ` +
        `WER ${(wer * 100).toFixed(2).padStart(6)} %  ${durationMs} ms\n`,
    );
  }

  const aggregateExpectedWords = cases.reduce((sum, item) => sum + item.expectedWords, 0);
  const aggregateActualWords = cases.reduce((sum, item) => sum + item.actualWords, 0);

  // Weighted aggregate: recompute distances over concatenated normalized text.
  const expectedAll = normalize(
    (
      await Promise.all(
        manifest.map((entry) => readFile(path.join(corpusDir, entry.groundTruth), 'utf8')),
      )
    ).join(' '),
  );
  const actualAll = normalize(
    (
      await Promise.all(
        manifest.map((entry) => readFile(path.join(resultsDir, 'raw', `${entry.id}.txt`), 'utf8')),
      )
    ).join(' '),
  );
  const aggregate = {
    expectedCharacters: expectedAll.length,
    actualCharacters: actualAll.length,
    expectedWords: aggregateExpectedWords,
    actualWords: aggregateActualWords,
    cer: rate(expectedAll, actualAll),
    wer: rate(expectedAll.split(' '), actualAll.split(' ')),
  };
  aggregate.cerPercent = aggregate.cer * 100;
  aggregate.werPercent = aggregate.wer * 100;

  const result = {
    generatedAt: new Date().toISOString(),
    service,
    corpus: {
      type: 'synthetic',
      manifest: path.relative(projectRoot, manifestPath).replaceAll('\\', '/'),
      cases: manifest.length,
    },
    methodology: {
      languageCodes: 'Tesseract ISO 639-2 (fra, eng, ara)',
      pageSegmentationMode: 'manifest psm, default 6',
      normalization: 'NFC, whitespace collapsed, line endings normalized',
      metrics: 'CER and WER computed with Levenshtein distance',
      aggregate: 'weighted over concatenated expected and recognized text',
      note: 'Synthetic corpus only; no customer acceptance claim.',
    },
    aggregate,
    cases,
  };

  await mkdir(resultsDir, { recursive: true });
  await writeFile(
    path.join(resultsDir, 'latest.json'),
    `${JSON.stringify(result, null, 2)}\n`,
    'utf8',
  );
  const report = markdownReport(result);
  await writeFile(path.join(resultsDir, 'latest.md'), report, 'utf8');
  process.stdout.write(`\n${report}`);

  if (maxCer !== null && aggregate.cer > maxCer) {
    process.stderr.write(
      `Aggregate CER ${(aggregate.cer * 100).toFixed(2)} % exceeds ` +
        `BENCHMARK_MAX_CER=${(maxCer * 100).toFixed(2)} %\n`,
    );
    process.exitCode = 1;
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error.message}\n`);
  process.exitCode = 1;
});
