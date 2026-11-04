import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { HistoryPanel } from '@/features/extraction/components/HistoryPanel';
import i18n from '@/i18n';
import type { HistoryEntry } from '@/api/types';

const history: HistoryEntry[] = [
  {
    id: 'doc-1',
    fileName: 'seed.pdf',
    date: '2026-10-27T08:00:00Z',
    status: 'COMPLETED',
  },
];

describe('HistoryPanel destructive actions', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('fr');
  });

  it('asks for confirmation before deleting a document', async () => {
    const onRemove = vi.fn();
    const onClear = vi.fn();
    render(
      <MemoryRouter>
        <HistoryPanel
          history={history}
          total={1}
          query=""
          status=""
          onQueryChange={vi.fn()}
          onStatusChange={vi.fn()}
          onRemove={onRemove}
          onClear={onClear}
        />
      </MemoryRouter>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Supprimer seed.pdf' }));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(onRemove).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Annuler' }));
    expect(onRemove).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Supprimer seed.pdf' }));
    const dialog = screen.getByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Supprimer' }));

    expect(onRemove).toHaveBeenCalledWith('doc-1');
    expect(onClear).not.toHaveBeenCalled();
  });

  it('asks for confirmation before clearing the user documents', async () => {
    const onRemove = vi.fn();
    const onClear = vi.fn();
    render(
      <MemoryRouter>
        <HistoryPanel
          history={history}
          total={1}
          query=""
          status=""
          onQueryChange={vi.fn()}
          onStatusChange={vi.fn()}
          onRemove={onRemove}
          onClear={onClear}
        />
      </MemoryRouter>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Vider mes documents' }));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(onClear).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Vider' }));

    expect(onClear).toHaveBeenCalledTimes(1);
    expect(onRemove).not.toHaveBeenCalled();
  });
});
