import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { AppLanguage } from '@/i18n/languages';
import { LANGUAGES, getDir } from '@/i18n/languages';

export function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const [current, setCurrent] = useState<AppLanguage>((i18n.language as AppLanguage) ?? 'fr');

  useEffect(() => {
    const handler = (lng: string) => setCurrent(lng as AppLanguage);
    i18n.on('languageChanged', handler);
    return () => i18n.off('languageChanged', handler);
  }, [i18n]);

  useEffect(() => {
    const dir = getDir(current);
    document.documentElement.dir = dir;
    document.documentElement.lang = current;
  }, [current]);

  function change(value: string) {
    void i18n.changeLanguage(value as AppLanguage);
  }

  return (
    <Select value={current} onValueChange={change}>
      <SelectTrigger className="w-[124px] sm:w-[140px]" aria-label={i18n.t('lang.label') as string}>
        <Globe className="h-4 w-4 me-2 shrink-0" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {LANGUAGES.map((l) => (
          <SelectItem key={l.value} value={l.value}>
            {l.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
