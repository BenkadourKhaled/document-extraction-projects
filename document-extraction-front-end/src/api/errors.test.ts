import { describe, expect, it } from 'vitest';
import { normalizeError } from '@/api/errors';

describe('normalizeError', () => {
  it('normalizes an axios response error with ProblemDetail', () => {
    const err = {
      isAxiosError: true,
      response: {
        status: 400,
        data: { title: 'Bad Request', detail: 'The PDF is password protected', status: 400 },
      },
      request: undefined,
      message: undefined,
    };
    const result = normalizeError(err);
    expect(result.status).toBe(400);
    expect(result.isPasswordProtected).toBe(true);
    expect(result.isNetwork).toBe(false);
    expect(result.detail).toContain('password');
  });

  it('normalizes a network error (no response)', () => {
    const err = {
      isAxiosError: true,
      response: undefined,
      request: {},
      message: 'Network Error',
    };
    const result = normalizeError(err);
    expect(result.isNetwork).toBe(true);
    expect(result.status).toBe(0);
  });

  it('detects too large from 413 status', () => {
    const err = {
      isAxiosError: true,
      response: { status: 413, data: { detail: 'Payload Too Large' } },
    };
    const result = normalizeError(err);
    expect(result.isTooLarge).toBe(true);
  });

  it('falls back to message for unknown errors', () => {
    const result = normalizeError(new Error('boom'));
    expect(result.detail).toBe('boom');
    expect(result.isNetwork).toBe(false);
  });
});
