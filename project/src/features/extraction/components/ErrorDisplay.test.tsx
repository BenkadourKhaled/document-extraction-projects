import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorDisplay } from '@/features/extraction/components/ErrorDisplay';
import type { ApiError } from '@/api/types';
import '@/i18n';

describe('ErrorDisplay', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows password protected message', () => {
    const error: ApiError = {
      status: 400,
      detail: 'The PDF is password protected',
      isNetwork: false,
      isPasswordProtected: true,
      isTooLarge: false,
      raw: null,
    };
    render(<ErrorDisplay error={error} />);
    expect(screen.getByText(/mot de passe|password/i)).toBeInTheDocument();
  });

  it('shows network error message', () => {
    const error: ApiError = {
      status: 0,
      detail: 'Network Error',
      isNetwork: true,
      isPasswordProtected: false,
      isTooLarge: false,
      raw: null,
    };
    render(<ErrorDisplay error={error} />);
    expect(screen.getByText(/Erreur réseau|Network error/i)).toBeInTheDocument();
  });

  it('shows API error detail', () => {
    const error: ApiError = {
      status: 500,
      detail: 'Internal server error',
      isNetwork: false,
      isPasswordProtected: false,
      isTooLarge: false,
      raw: null,
    };
    render(<ErrorDisplay error={error} />);
    expect(screen.getByText(/Internal server error/)).toBeInTheDocument();
  });

  it('shows too large message for 413', () => {
    const error: ApiError = {
      status: 413,
      detail: 'Payload Too Large',
      isNetwork: false,
      isPasswordProtected: false,
      isTooLarge: true,
      raw: null,
    };
    render(<ErrorDisplay error={error} />);
    expect(screen.getByText(/50 Mo|50 MB/i)).toBeInTheDocument();
  });
});
