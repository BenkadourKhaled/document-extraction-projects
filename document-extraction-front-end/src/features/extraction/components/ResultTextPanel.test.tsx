import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ResultTextPanel } from '@/features/extraction/components/ResultTextPanel';
import type { ExtractedField, Extraction, FieldSource } from '@/api/types';
import { resetStore, seedExtraction, seedFields } from '@/test/mocks/handlers';
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

const referenceField: ExtractedField = {
  id: 'field-1',
  documentId: 'ext-1',
  lineageId: 'ext-1',
  fieldKey: 'reference_number',
  label: 'Numéro de référence',
  fieldType: 'IDENTIFIER',
  rawValue: '2026-001',
  normalizedValue: '2026-001',
  effectiveValue: '2026-001',
  verificationStatus: 'VERIFIED',
  confidence: 1,
  extractionMethod: 'RULE',
  extractorVersion: 'rules-reference-v1',
  corrected: false,
  sources: [
    {
      id: 'source-1',
      pageNumber: 1,
      bboxX0: 0.125,
      bboxY0: 0.25,
      bboxX1: 0.5,
      bboxY1: 0.375,
      sourceSpace: 'PDF_POINTS',
      pageWidth: 612,
      pageHeight: 792,
      rotationDeg: 0,
      charStart: 15,
      charEnd: 23,
      sourceText: 'Facture numero 2026-001',
      ordinal: 0,
    },
  ],
  corrections: [],
};

function renderPanel(
  overrides?: Partial<Extraction>,
  onSourceSelect?: (field: ExtractedField, source: FieldSource | null) => void,
) {
  const onNew = vi.fn();
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  void i18n.changeLanguage('fr');
  const utils = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <ResultTextPanel
          extraction={{ ...extraction, ...overrides }}
          onNewExtraction={onNew}
          onSourceSelect={onSourceSelect}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, onNew };
}

describe('ResultTextPanel', () => {
  beforeEach(() => {
    resetStore();
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

  it('downloads the authoritative .txt export from the server', async () => {
    URL.createObjectURL = vi.fn().mockReturnValue('blob:fake');
    URL.revokeObjectURL = vi.fn();
    seedExtraction('ext-1', 'COMPLETED', { fileName: 'doc.pdf' });
    const clickSpy = vi.fn();
    const clickMock = vi.spyOn(HTMLElement.prototype, 'click').mockImplementation(function (this: HTMLElement) {
      clickSpy();
    });
    renderPanel();
    const btn = screen.getByRole('button', { name: /\.txt/ });
    fireEvent.click(btn);
    await waitFor(() => expect(clickSpy).toHaveBeenCalled());
    const anchor = clickMock.mock.instances.at(-1) as unknown as HTMLAnchorElement;
    expect(anchor.download).toBe('doc.txt');
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

  it('lists traceable fields, highlights sources and appends corrections', async () => {
    const user = userEvent.setup();
    seedFields('ext-1', [referenceField]);
    const onSourceSelect = vi.fn();
    renderPanel(undefined, onSourceSelect);

    await user.click(screen.getByRole('tab', { name: i18n.t('fields.tab') }));

    const row = await screen.findByTestId('field-reference_number');
    expect(within(row).getAllByText('2026-001').length).toBeGreaterThan(0);
    expect(within(row).getByText(i18n.t('fields.status.VERIFIED'))).toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: i18n.t('fields.pageSource', { page: 1 }) }));
    expect(onSourceSelect).toHaveBeenCalledWith(
      expect.objectContaining({ fieldKey: 'reference_number' }),
      expect.objectContaining({ pageNumber: 1, sourceSpace: 'PDF_POINTS' }),
    );

    await user.click(within(row).getByRole('button', { name: i18n.t('fields.correct') }));
    const input = await screen.findByLabelText(i18n.t('fields.value'));
    await user.clear(input);
    await user.type(input, 'FACT-2026-777');
    await user.type(
      screen.getByLabelText(i18n.t('fields.reason')),
      'Vérification manuelle',
    );
    await user.click(screen.getByRole('button', { name: i18n.t('fields.save') }));

    const updatedRow = await screen.findByTestId('field-reference_number');
    await waitFor(() =>
      expect(within(updatedRow).getByText('FACT-2026-777')).toBeInTheDocument(),
    );
    expect(within(updatedRow).getByText(i18n.t('fields.correctedBadge'))).toBeInTheDocument();
    // The raw value must remain visible after correction.
    expect(within(updatedRow).getAllByText('2026-001').length).toBeGreaterThan(0);

    await user.click(within(updatedRow).getByRole('button', { name: i18n.t('fields.correct') }));
    expect(await screen.findByText(i18n.t('fields.history'))).toBeInTheDocument();
    expect(screen.getByText(i18n.t('fields.active'))).toBeInTheDocument();
    expect(screen.getByText('Vérification manuelle')).toBeInTheDocument();
  });
});
