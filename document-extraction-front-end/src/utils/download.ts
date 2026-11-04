import type { Extraction } from '@/api/types';

export function downloadBlob(content: string, filename: string, mime: string): void {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function downloadTxt(extraction: Extraction): void {
  const baseName = extraction.fileName.replace(/\.[^./\\]+$/, '');
  downloadBlob(extraction.fullText, `${baseName}.txt`, 'text/plain;charset=utf-8');
}

export function downloadJson(extraction: Extraction): void {
  const baseName = extraction.fileName.replace(/\.[^./\\]+$/, '');
  const payload = JSON.stringify(extraction, null, 2);
  downloadBlob(payload, `${baseName}.json`, 'application/json;charset=utf-8');
}
