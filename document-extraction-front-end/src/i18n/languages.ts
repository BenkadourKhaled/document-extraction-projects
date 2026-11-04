export type AppLanguage = 'fr' | 'en' | 'ar';

export interface LanguageOption {
  value: AppLanguage;
  label: string;
  dir: 'ltr' | 'rtl';
}

export const LANGUAGES: LanguageOption[] = [
  { value: 'fr', label: 'Français', dir: 'ltr' },
  { value: 'en', label: 'English', dir: 'ltr' },
  { value: 'ar', label: 'العربية', dir: 'rtl' },
];

export function getDir(lang: AppLanguage): 'ltr' | 'rtl' {
  return lang === 'ar' ? 'rtl' : 'ltr';
}
