import { useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { ExtractionStatus, HistoryEntry } from '@/api/types';
import { clearDocuments, deleteExtraction, listDocuments } from '@/api/extractionService';
import { normalizeError } from '@/api/errors';

const PAGE_SIZE = 50;
const FILTERS_STORAGE_KEY = 'docx.history.filters.v1';

type HistoryFilters = {
  query: string;
  statuses: ExtractionStatus[];
  from: string;
  to: string;
};

function loadFilters(): HistoryFilters {
  try {
    const raw = sessionStorage.getItem(FILTERS_STORAGE_KEY);
    if (!raw) return { query: '', statuses: [], from: '', to: '' };
    const parsed = JSON.parse(raw) as Partial<HistoryFilters>;
    return {
      query: parsed.query ?? '',
      statuses: Array.isArray(parsed.statuses) ? parsed.statuses : [],
      from: parsed.from ?? '',
      to: parsed.to ?? '',
    };
  } catch {
    return { query: '', statuses: [], from: '', to: '' };
  }
}

function saveFilters(filters: HistoryFilters) {
  try {
    sessionStorage.setItem(FILTERS_STORAGE_KEY, JSON.stringify(filters));
  } catch {
    // ignore
  }
}

export function useExtractionHistory(): {
  history: HistoryEntry[];
  total: number;
  query: string;
  status: ExtractionStatus | '';
  statuses: ExtractionStatus[];
  from: string;
  to: string;
  setQuery: (value: string) => void;
  setStatus: (value: ExtractionStatus | '') => void;
  setStatuses: (value: ExtractionStatus[]) => void;
  setFrom: (value: string) => void;
  setTo: (value: string) => void;
  isLoading: boolean;
  isFetching: boolean;
  hasMore: boolean;
  isLoadingMore: boolean;
  loadMore: () => void;
  remove: (id: string) => void;
  clear: () => void;
  refresh: () => void;
  updateStatus: (id: string, status: HistoryEntry['status']) => void;
} {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const initial = useMemo(() => loadFilters(), []);
  const [query, setQuery] = useState(initial.query);
  const [statuses, setStatuses] = useState<ExtractionStatus[]>(initial.statuses);
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const deferredQuery = useDeferredValue(query);
  const deferredStatuses = useDeferredValue(statuses);
  const deferredFrom = useDeferredValue(from);
  const deferredTo = useDeferredValue(to);

  useEffect(() => {
    saveFilters({ query, statuses, from, to });
  }, [query, statuses, from, to]);

  const status = statuses.length === 1 ? statuses[0] : '';

  const documentsQuery = useInfiniteQuery({
    queryKey: ['documents', deferredQuery, deferredStatuses.join(','), deferredFrom, deferredTo],
    queryFn: ({ pageParam }) =>
      listDocuments({
        size: PAGE_SIZE,
        page: pageParam,
        query: deferredQuery.trim() || undefined,
        status: deferredStatuses.length > 0 ? deferredStatuses : undefined,
        from: deferredFrom || undefined,
        to: deferredTo || undefined,
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => {
      const loaded = allPages.reduce((count, page) => count + page.items.length, 0);
      return loaded < lastPage.total ? allPages.length : undefined;
    },
    staleTime: 2000,
  });

  const history: HistoryEntry[] =
    documentsQuery.data?.pages.flatMap((page) =>
      page.items.map((document) => ({
        id: document.id,
        fileName: document.fileName,
        date: document.createdAt,
        status: document.status,
      })),
    ) ?? [];

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['documents'] });
    void queryClient.invalidateQueries({ queryKey: ['document-stats'] });
  }, [queryClient]);

  const remove = useCallback(
    (id: string) => {
      void (async () => {
        try {
          await deleteExtraction(id);
        } catch (error) {
          const normalized = normalizeError(error);
          toast.error(
            t(normalized.isNetwork ? 'errors.network' : 'errors.deleteFailed'),
          );
        } finally {
          invalidate();
        }
      })();
    },
    [invalidate, t],
  );

  const clear = useCallback(() => {
    void (async () => {
      try {
        await clearDocuments();
      } catch (error) {
        const normalized = normalizeError(error);
        toast.error(
          t(normalized.isNetwork ? 'errors.network' : 'errors.clearFailed'),
        );
      } finally {
        invalidate();
      }
    })();
  }, [invalidate, t]);

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['documents'] });
  }, [queryClient]);

  const updateStatus = useCallback(() => {
    invalidate();
  }, [invalidate]);

  const loadMore = useCallback(() => {
    if (documentsQuery.hasNextPage && !documentsQuery.isFetchingNextPage) {
      void documentsQuery.fetchNextPage();
    }
  }, [documentsQuery]);

  const setStatusValue = useCallback((value: ExtractionStatus | '') => {
    setStatuses(value ? [value] : []);
  }, []);

  return {
    history,
    total: documentsQuery.data?.pages[0]?.total ?? history.length,
    query,
    status,
    statuses,
    from,
    to,
    setQuery,
    setStatus: setStatusValue,
    setStatuses,
    setFrom,
    setTo,
    isLoading: documentsQuery.isLoading,
    isFetching: documentsQuery.isFetching,
    hasMore: Boolean(documentsQuery.hasNextPage),
    isLoadingMore: documentsQuery.isFetchingNextPage,
    loadMore,
    remove,
    clear,
    refresh,
    updateStatus,
  };
}
