import { useEffect, useState } from 'react';
import { getStoredTheme, storeTheme, applyTheme, type Theme } from '@/utils/theme';

export function useTheme(): [Theme, (t: Theme) => void, () => void] {
  const [theme, setThemeState] = useState<Theme>(() => getStoredTheme());

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  function setTheme(t: Theme): void {
    storeTheme(t);
    setThemeState(t);
  }

  function toggle(): void {
    setTheme(theme === 'light' ? 'dark' : 'light');
  }

  return [theme, setTheme, toggle];
}
