import type { ApiError, ProblemDetail } from './types';

const PASSWORD_HINTS = ['password', 'mot de passe', 'encrypted', 'chiffr'];
const TOO_LARGE_HINTS = ['too large', 'trop volumineux', 'size', 'maximum', 'payload too large', '413'];

function looksLike(value: string, hints: string[]): boolean {
  const lower = value.toLowerCase();
  return hints.some((h) => lower.includes(h));
}

function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'status' in value &&
    'detail' in value &&
    'isNetwork' in value &&
    'isPasswordProtected' in value &&
    'isTooLarge' in value
  );
}

export function normalizeError(error: unknown): ApiError {
  // The Axios response interceptor already normalizes API errors.
  if (isApiError(error)) return error;

  if (typeof error === 'object' && error !== null && 'isAxiosError' in error) {
    const axiosErr = error as {
      response?: { status?: number; data?: ProblemDetail };
      request?: unknown;
      message?: string;
    };

    if (axiosErr.response) {
      const status = axiosErr.response.status ?? 500;
      const problem: ProblemDetail = axiosErr.response.data ?? {};
      const detail = problem.detail ?? problem.title ?? `HTTP ${status}`;
      return {
        status,
        detail,
        isNetwork: false,
        isPasswordProtected:
          (status === 400 || status === 422) && looksLike(detail, PASSWORD_HINTS),
        isTooLarge: status === 413 || looksLike(detail, TOO_LARGE_HINTS),
        raw: problem,
      };
    }

    if (axiosErr.request) {
      return {
        status: 0,
        detail: axiosErr.message ?? 'Network error',
        isNetwork: true,
        isPasswordProtected: false,
        isTooLarge: false,
        raw: null,
      };
    }
  }

  const message = error instanceof Error ? error.message : 'Unknown error';
  return {
    status: 0,
    detail: message,
    isNetwork: false,
    isPasswordProtected: false,
    isTooLarge: false,
    raw: null,
  };
}
