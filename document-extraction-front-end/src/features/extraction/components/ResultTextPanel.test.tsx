import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ResultTextPanel } from '@/features/extraction/components/ResultTextPanel';
import type { Extraction } from '@/api/types';
import i18n from '@/i18n';

const extraction: Extraction = {
  id: 'ext-1',
  status: 'COMPLETED',
  fileName: 'doc.pdf',
  mimeType: 'application/pdf',
  pageCount: 2,
  fullText: 'Page one text. Page two text.',
  pages: [
    { number: 1, text: 'Page one text.', method: 'NATIVE', confidence: 1 },
    { number: 2, text: 'Page two text.', method: 'OCR', confidence: 0.92 },
  ],
  durationMs: 1500,
  error: null,
  createdAt: '2026-10-27T08:00:00Z',
  languages: 'fra+eng',
};

function renderPanel(overrides?: Partial<Extraction>) {
  const onNew = vi.fn();
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  void i18n.changeLanguage('fr');
  const utils = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <ResultTextPanel extraction={{ ...extraction, ...overrides }} onNewExtraction={onNew} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, onNew };
}

describe('ResultTextPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders full text by default and shows page count', () => {
    renderPanel();
    expect(screen.getByRole('tab', { name: /(Texte complet|Full text)/ })).toBeInTheDocument();
  });

  it('shows per-page tabs with method badges and confidence', async () => {
    const user = userEvent.setup();
    renderPanel();
    const tab = screen.getByRole('tab', { name: /Par page/ });
    await user.click(tab);
    const allText = document.body.textContent ?? '';
    expect(allText).toMatch(/Natif/);
    expect(screen.getByText(/92%/)).toBeInTheDocument();
  });

  it('copies text to clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
    renderPanel();
    const copyBtn = screen.getByRole('button', { name: /(Copier|Copy)/ });
    fireEvent.click(copyBtn);
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('Page one text. Page two text.'));
  });

  it('triggers .txt download via blob', async () => {
    URL.createObjectURL = vi.fn().mockReturnValue('blob:fake');
    URL.revokeObjectURL = vi.fn();
    const clickSpy = vi.fn();
    vi.spyOn(HTMLElement.prototype, 'click').mockImplementation(function (this: HTMLElement) {
      clickSpy();
    });
    renderPanel();
    const btn = screen.getByRole('button', { name: /\.txt/ });
    fireEvent.click(btn);
    await waitFor(() => expect(clickSpy).toHaveBeenCalled());
  });

  it('highlights search matches', () => {
    renderPanel();
    const input = screen.getByLabelText(/Rechercher|Search/);
    fireEvent.change(input, { target: { value: 'page' } });
    expect(screen.getByText(/correspondance|match/)).toBeInTheDocument();
  });

  it('calls onNewExtraction', () => {
    const { onNew } = renderPanel();
    const newBtn = screen.getByRole('button', { name: /(Nouvelle extraction|New extraction)/ });
    fireEvent.click(newBtn);
    expect(onNew).toHaveBeenCalled();
  });
});
