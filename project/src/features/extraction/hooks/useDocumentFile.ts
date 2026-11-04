import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getExtractionFile } from '@/api/extractionService';

export function useDocumentFile(
  id: string | null,
  enabled: boolean,
  fileName?: string,
  mimeType?: string,
): { file: File | null; isLoading: boolean } {
  const query = useQuery({
    queryKey: ['extraction', 'file', id],
    queryFn: ({ signal }) => getExtractionFile(id as string, signal),
    enabled: Boolean(id) && enabled,
    staleTime: Infinity,
    gcTime: 5 * 60 * 1000,
    retry: false,
  });

  const file = useMemo(() => {
    if (!query.data) return null;
    return new File([query.data], fileName ?? 'document', {
      type: mimeType || query.data.type || 'application/octet-stream',
    });
  }, [query.data, fileName, mimeType]);

  return { file, isLoading: query.isLoading };
}
