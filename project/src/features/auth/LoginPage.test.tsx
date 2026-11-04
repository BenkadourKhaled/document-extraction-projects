import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { LoginPage } from '@/features/auth/LoginPage';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { server } from '@/test/mocks/server';
import i18n from '@/i18n';

const API = import.meta.env.VITE_API_URL ?? '/api/v1';

function renderLogin() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<div>Espace de travail</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(async () => {
    localStorage.clear();
    await i18n.changeLanguage('fr');
  });

  it('opens the workspace after a successful login', async () => {
    renderLogin();

    await userEvent.type(screen.getByLabelText(i18n.t('auth.email')), 'admin@test.local');
    await userEvent.type(screen.getByLabelText(i18n.t('auth.password')), 'TestPassword123!');
    await userEvent.click(screen.getByRole('button', { name: i18n.t('auth.signIn') }));

    expect(await screen.findByText('Espace de travail')).toBeInTheDocument();
    expect(localStorage.getItem('docx.auth.token')).toBe('test-token');
  });

  it('shows a clear error for invalid credentials', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json(
          {
            title: 'Unauthorized',
            detail: 'Email ou mot de passe incorrect.',
            status: 401,
          },
          { status: 401 },
        ),
      ),
    );

    renderLogin();

    await userEvent.type(screen.getByLabelText(i18n.t('auth.email')), 'admin@test.local');
    await userEvent.type(screen.getByLabelText(i18n.t('auth.password')), 'bad-password');
    await userEvent.click(screen.getByRole('button', { name: i18n.t('auth.signIn') }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Email ou mot de passe incorrect.'),
    );
    expect(localStorage.getItem('docx.auth.token')).toBeNull();
  });
});
