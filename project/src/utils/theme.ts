const KEY = 'docx.theme';

export type Theme = 'light' | 'dark';

export function getStoredTheme(): Theme {
  try {
    const v = localStorage.getItem(KEY);
    if (v === 'light' || v === 'dark') return v;
  } catch {
    // ignore
  }
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

export function storeTheme(t: Theme): void {
  try {
    localStorage.setItem(KEY, t);
  } catch {
    // ignore
  }
}

export function applyTheme(t: Theme): void {
  const root = document.documentElement;
  root.classList.toggle('dark', t === 'dark');
  root.style.colorScheme = t;
}
