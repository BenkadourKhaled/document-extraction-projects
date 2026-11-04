import { useEffect } from 'react';
import { useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { getExtraction } from '@/api/extractionService';
import type { ApiError, Extraction, ExtractionStatus } from '@/api/types';

const POLL_INTERVAL = 1500;
const ACTIVE_STATUSES = new Set<ExtractionStatus>(['PENDING', 'PROCESSING']);
const TERMINAL_STATUSES = new Set<ExtractionStatus>(['COMPLETED', 'FAILED', 'CANCELLED']);

export function useExtractionStatus(id: string | null): UseQueryResult<Extraction, ApiError> {
  const queryClient = useQueryClient();
  const query = useQuery<Extraction, ApiError>({
    queryKey: ['extraction', 'status', id],
    queryFn: ({ signal }) => getExtraction(id as string, signal),
    enabled: id !== null,
    refetchInterval: (current) => {
      const status = current.state.data?.status;
      if (status && ACTIVE_STATUSES.has(status)) {
        return POLL_INTERVAL;
      }
      return false;
    },
    refetchIntervalInBackground: true,
    retry: false,
  });

  const status = query.data?.status;
  useEffect(() => {
    if (status && TERMINAL_STATUSES.has(status)) {
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
    }
  }, [status, queryClient]);

  return query;
}
