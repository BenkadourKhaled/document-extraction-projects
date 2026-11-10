import { apiClient } from './client';
import type { ExtractedField, FieldCorrectionInput } from './types';

const BASE = '/extractions';

export async function getExtractionFields(
  id: string,
  signal?: AbortSignal,
): Promise<ExtractedField[]> {
  const { data } = await apiClient.get<ExtractedField[]>(`${BASE}/${id}/fields`, { signal });
  return data;
}

export async function correctExtractionField(
  id: string,
  fieldKey: string,
  input: FieldCorrectionInput,
): Promise<ExtractedField> {
  const { data } = await apiClient.post<ExtractedField>(
    `${BASE}/${id}/fields/${encodeURIComponent(fieldKey)}/corrections`,
    input,
  );
  return data;
}
