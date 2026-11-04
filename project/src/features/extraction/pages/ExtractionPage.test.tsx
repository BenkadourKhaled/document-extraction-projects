import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { ExtractionPage } from '@/features/extraction/pages/ExtractionPage';
import { Header } from '@/features/extraction/components/Header';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { server } from '@/test/mocks/server';
import { resetStore, seedExtraction } from '@/test/mocks/handlers';
import i18n from '@/i18n';

const API = import.meta.env.VITE_API_URL ?? '/api/v1';

function renderPage(initialPath = '/') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Header />
          <Routes>
            <Route path="/" element={<ExtractionPage />} />
            <Route path="/extraction/:id" element={<ExtractionPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('ExtractionPage navigation', () => {
  beforeEach(async () => {
    localStorage.clear();
    resetStore();
    vi.restoreAllMocks();
    await i18n.changeLanguage('fr');
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(() => 'blob:preview'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(),
    });
  });

  it('returns to the home view in one click from the header', async () => {
    seedExtraction('ext-nav', 'COMPLETED', { fileName: 'seed.png', mimeType: 'image/png' });
    renderPage('/extraction/ext-nav');

    expect(await screen.findByRole('tab', { name: i18n.t('result.fullText') })).toBeInTheDocument();

    const header = screen.getByRole('banner');
    await userEvent.click(within(header).getByRole('link', { name: i18n.t('nav.new') }));

    expect(await screen.findByText(i18n.t('upload.title'))).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: i18n.t('result.fullText') })).not.toBeInTheDocument();
  });

  it('searches history on the server', async () => {
    seedExtraction('ext-invoice', 'COMPLETED', { fileName: 'invoice.pdf' });
    seedExtraction('ext-scan', 'FAILED', { fileName: 'scan.png', mimeType: 'image/png' });
    renderPage();

    const search = await screen.findByPlaceholderText(i18n.t('history.searchPlaceholder'));
    await userEvent.type(search, 'invoice');

    await waitFor(() => expect(screen.queryByText('scan.png')).not.toBeInTheDocument());
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument();
  });

  it('keeps the uploaded document preview after navigating to the result', async () => {
    renderPage();

    const input = screen.getByLabelText('Choisir un fichier');
    const file = new File(['image bytes'], 'scan.png', { type: 'image/png' });
    await userEvent.upload(input, file);

    const start = await screen.findByRole('button', { name: /lancer l'extraction/i });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start);

    expect(await screen.findByRole('tab', { name: i18n.t('result.fullText') })).toBeInTheDocument();
    expect(await screen.findByAltText(i18n.t('result.preview'))).toHaveAttribute('src', 'blob:preview');
  });
});

describe('ExtractionPage upload errors', () => {
  beforeEach(async () => {
    localStorage.clear();
    vi.restoreAllMocks();
    await i18n.changeLanguage('fr');
  });

  it('shows the ProblemDetail detail when the upload fails before an id exists', async () => {
    server.use(
      http.post(`${API}/extractions`, () =>
        HttpResponse.json(
          {
            title: 'Bad Request',
            detail: 'Le fichier PDF est corrompu.',
            status: 400,
          },
          { status: 400 },
        ),
      ),
    );

    renderPage();

    const input = screen.getByLabelText('Choisir un fichier');
    const file = new File(['not a real pdf'], 'corrupt.pdf', { type: 'application/pdf' });
    await userEvent.upload(input, file);

    const start = await screen.findByRole('button', { name: /lancer l'extraction/i });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start);

    expect(await screen.findByText(/Le fichier PDF est corrompu/)).toBeInTheDocument();
    expect(screen.queryByText(/Déposez votre fichier ici/)).not.toBeInTheDocument();
  });
});
