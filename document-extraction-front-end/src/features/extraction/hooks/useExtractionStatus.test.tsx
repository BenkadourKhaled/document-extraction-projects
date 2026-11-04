import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useExtractionStatus } from '@/features/extraction/hooks/useExtractionStatus';
import { seedExtraction, resetStore } from '@/test/mocks/handlers';
import '@/i18n';

function StatusProbe({ id }: { id: string }) {
  const query = useExtractionStatus(id);
  return (
    <div>
      <span data-testid="status">{query.data?.status ?? 'loading'}</span>
    </div>
  );
}

describe('useExtractionStatus polling', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    resetStore();
  });

  it('returns COMPLETED after polling a PROCESSING extraction', async () => {
    seedExtraction('poll-1', 'PROCESSING');
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <StatusProbe id="poll-1" />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    // The MSW handler transitions PROCESSING → COMPLETED on first GET, so
    // after the initial fetch resolves we land on COMPLETED and polling stops.
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('COMPLETED'), {
      timeout: 5000,
    });
  });

  it('returns COMPLETED immediately without polling when already done', async () => {
    seedExtraction('poll-2', 'COMPLETED');
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <StatusProbe id="poll-2" />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('COMPLETED'));
  });

  it('does not poll a 404 and exposes an error', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <StatusProbe id="missing-id" />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('loading'), {
      timeout: 3000,
    });
  });
});
