import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { UserManagementPage } from '@/features/users/UserManagementPage';
import { resetStore } from '@/test/mocks/handlers';
import i18n from '@/i18n';

function renderUsersPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/admin/users']}>
          <ProtectedRoute>
            <UserManagementPage />
          </ProtectedRoute>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('UserManagementPage', () => {
  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem('docx.auth.token', 'test-token');
    resetStore();
    await i18n.changeLanguage('fr');
  });

  it('lists organization users and creates a new account', async () => {
    renderUsersPage();

    expect(await screen.findByText('Test Admin')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Créer un utilisateur' }));

    await userEvent.type(screen.getByLabelText('Nom complet'), 'Nouvel Utilisateur');
    await userEvent.type(screen.getByLabelText('Adresse e-mail'), 'nouveau@test.local');
    await userEvent.type(screen.getByLabelText('Mot de passe initial'), 'NouveauPass123!');
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte' }));

    await waitFor(() =>
      expect(screen.getByText('Nouvel Utilisateur')).toBeInTheDocument(),
    );
    expect(screen.getByText('nouveau@test.local')).toBeInTheDocument();
  });
});
