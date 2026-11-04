import type { OcrLanguage } from '@/api/types';

const KEY = 'docx.languages';

export function loadLanguages(): OcrLanguage[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return ['fr'];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return ['fr'];
    const valid = parsed.filter((l): l is OcrLanguage =>
      ['fr', 'en', 'ar'].includes(l)
    );
    return valid.length > 0 ? valid : ['fr'];
  } catch {
    return ['fr'];
  }
}

export function saveLanguages(languages: OcrLanguage[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(languages));
  } catch {
    // ignore
  }
}
