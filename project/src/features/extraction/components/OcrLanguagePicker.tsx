import { useTranslation } from 'react-i18next';
import { Check, Languages } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { OcrLanguage } from '@/api/types';

const OCR_LANGS: { value: OcrLanguage; label: string; flag: string }[] = [
  { value: 'fr', label: 'Français', flag: 'FR' },
  { value: 'en', label: 'English', flag: 'EN' },
  { value: 'ar', label: 'العربية', flag: 'AR' },
];

interface OcrLanguagePickerProps {
  value: OcrLanguage[];
  onChange: (next: OcrLanguage[]) => void;
}

export function OcrLanguagePicker({ value, onChange }: OcrLanguagePickerProps) {
  const { t } = useTranslation();

  function toggle(lang: OcrLanguage) {
    if (value.includes(lang)) {
      if (value.length === 1) return;
      onChange(value.filter((l) => l !== lang));
    } else {
      onChange([...value, lang]);
    }
  }

  return (
    <fieldset className="space-y-3">
      <legend className="flex w-full items-start gap-3">
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
          <Languages className="h-4 w-4" />
        </span>
        <span className="min-w-0">
          <span className="block text-sm font-medium">{t('upload.language')}</span>
          <span className="mt-0.5 block text-xs leading-relaxed text-muted-foreground">
            {t('upload.languageHint')}
          </span>
        </span>
      </legend>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 lg:grid-cols-1">
        {OCR_LANGS.map((l) => {
          const active = value.includes(l.value);
          return (
            <button
              key={l.value}
              type="button"
              aria-pressed={active}
              aria-label={l.label}
              onClick={() => toggle(l.value)}
              disabled={active && value.length === 1}
              className={cn(
                'group flex min-h-[3.25rem] w-full items-center gap-2.5 rounded-xl border px-3 py-2.5 text-start transition-all duration-200',
                'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
                active
                  ? 'border-primary/40 bg-primary/[0.07] text-foreground shadow-sm'
                  : 'border-border/70 bg-background/50 text-muted-foreground hover:border-primary/30 hover:bg-accent/30 hover:text-foreground',
                active && value.length === 1 && 'cursor-not-allowed',
              )}
            >
              <span
                className={cn(
                  'grid h-7 w-7 shrink-0 place-items-center rounded-lg border text-[9px] font-bold tracking-wide transition-colors',
                  active
                    ? 'border-primary/20 bg-primary text-primary-foreground'
                    : 'border-border/70 bg-muted/60 text-muted-foreground',
                )}
              >
                {l.flag}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{l.label}</span>
              <span
                className={cn(
                  'grid h-4 w-4 shrink-0 place-items-center rounded-full border transition-colors',
                  active
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'border-muted-foreground/30 text-transparent',
                )}
              >
                <Check className="h-2.5 w-2.5" />
              </span>
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
