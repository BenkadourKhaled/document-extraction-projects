import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ChangePasswordDialog } from '@/features/auth/ChangePasswordDialog';
import i18n from '@/i18n';

function renderDialog(onOpenChange = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <ChangePasswordDialog open onOpenChange={onOpenChange} />
    </QueryClientProvider>,
  );

  return onOpenChange;
}

describe('ChangePasswordDialog', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('fr');
  });

  it('blocks submission when the confirmation does not match', async () => {
    const onOpenChange = renderDialog();

    await userEvent.type(screen.getByLabelText('Mot de passe actuel'), 'CurrentPass123!');
    await userEvent.type(screen.getByLabelText('Nouveau mot de passe'), 'NouveauPass123!');
    await userEvent.type(
      screen.getByLabelText('Confirmer le nouveau mot de passe'),
      'DifferentPass123!',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Mettre à jour' }));

    expect(onOpenChange).not.toHaveBeenCalled();
  });

  it('submits a valid password change and closes', async () => {
    const onOpenChange = renderDialog();

    await userEvent.type(screen.getByLabelText('Mot de passe actuel'), 'CurrentPass123!');
    await userEvent.type(screen.getByLabelText('Nouveau mot de passe'), 'NouveauPass123!');
    await userEvent.type(
      screen.getByLabelText('Confirmer le nouveau mot de passe'),
      'NouveauPass123!',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Mettre à jour' }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});
