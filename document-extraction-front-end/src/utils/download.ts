import { downloadExtraction } from '@/api/extractionService';

export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function downloadBlob(content: string, filename: string, mime: string): void {
  saveBlob(new Blob([content], { type: mime }), filename);
}

export function baseName(fileName: string): string {
  return fileName.replace(/\.[^./\\]+$/, '');
}

/**
 * Downloads the authoritative server export. The backend includes the full
 * text and the effective field values, including reviewer corrections, so the
 * browser never rebuilds a divergent export locally.
 */
export async function downloadServerExtraction(
  id: string,
  fileName: string,
  format: 'txt' | 'json',
  signal?: AbortSignal,
): Promise<void> {
  const blob = await downloadExtraction(id, format, signal);
  saveBlob(blob, `${baseName(fileName)}.${format}`);
}
