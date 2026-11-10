import { apiClient } from './client';
import type {
  DocumentSummary,
  Extraction,
  OcrLanguage,
  PageResponse,
} from './types';

const BASE = '/extractions';

// The API expects ISO 639-2/Tesseract codes (fra, eng, ara), not ISO 639-1.
const OCR_LANGUAGE_CODES: Record<OcrLanguage, string> = {
  fr: 'fra',
  en: 'eng',
  ar: 'ara',
};

function buildLanguagesParam(languages: OcrLanguage[]): string | undefined {
  if (languages.length === 0) return undefined;
  return languages.map((language) => OCR_LANGUAGE_CODES[language]).join('+');
}

export async function createExtraction(
  file: File,
  languages: OcrLanguage[],
  signal?: AbortSignal,
): Promise<Extraction> {
  const form = new FormData();
  form.append('file', file);
  const languagesParam = buildLanguagesParam(languages);
  if (languagesParam) {
    form.append('languages', languagesParam);
  }
  const { data } = await apiClient.post<Extraction>(BASE, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,
  });
  return data;
}

export async function createExtractions(
  files: File[],
  languages: OcrLanguage[],
  signal?: AbortSignal,
): Promise<Extraction[]> {
  const form = new FormData();
  files.forEach((file) => form.append('files', file));
  const languagesParam = buildLanguagesParam(languages);
  if (languagesParam) {
    form.append('languages', languagesParam);
  }
  const { data } = await apiClient.post<{ items: Extraction[] } | Extraction[]>(
    BASE,
    form,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
    },
  );
  if (Array.isArray(data)) return data;
  return data.items ?? [];
}

export async function reprocessExtraction(
  id: string,
  languages: OcrLanguage[],
): Promise<Extraction> {
  const codes = languages.map((language) => OCR_LANGUAGE_CODES[language]);
  const { data } = await apiClient.post<Extraction>(`${BASE}/${id}/reprocess`, {
    languages: codes,
  });
  return data;
}

export async function getExtraction(id: string, signal?: AbortSignal): Promise<Extraction> {
  const { data } = await apiClient.get<Extraction>(`${BASE}/${id}`, { signal });
  return data;
}

export async function getExtractionFile(id: string, signal?: AbortSignal): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(`${BASE}/${id}/file`, {
    responseType: 'blob',
    signal,
  });
  return data;
}

export async function downloadExtraction(
  id: string,
  format: 'txt' | 'json',
  signal?: AbortSignal,
): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(`${BASE}/${id}/download`, {
    params: { format },
    responseType: 'blob',
    signal,
  });
  return data;
}

export async function deleteExtraction(id: string): Promise<void> {
  await apiClient.delete(`${BASE}/${id}`);
}

export async function listDocuments(params?: {
  query?: string;
  status?: string | string[];
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<DocumentSummary>> {
  const { data } = await apiClient.get<PageResponse<DocumentSummary>>('/documents', { params });
  return data;
}

export async function clearDocuments(status?: string): Promise<void> {
  await apiClient.delete('/documents', { params: status ? { status } : undefined });
}

export async function downloadDocumentsZip(
  ids: string[],
  format: 'txt' | 'json' = 'txt',
): Promise<Blob> {
  const { data } = await apiClient.post<Blob>(
    '/documents/zip',
    { ids, format },
    { responseType: 'blob' },
  );
  return data;
}
