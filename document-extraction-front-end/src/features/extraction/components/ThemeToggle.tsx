import { Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { useTheme } from '@/hooks/useTheme';

export function ThemeToggle() {
  const { t } = useTranslation();
  const [theme, , toggle] = useTheme();
  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label={t('theme.toggle') as string}>
      {theme === 'light' ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
    </Button>
  );
}
