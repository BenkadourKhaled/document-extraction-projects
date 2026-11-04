const TOKEN_KEY = 'docx.auth.token';

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function saveToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // ignore storage errors
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore storage errors
  }
}
