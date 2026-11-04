import { useTranslation } from 'react-i18next';
import { Loader2, X, CheckCircle2, AlertTriangle, Clock, Ban } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { ExtractionStatus } from '@/api/types';
import { cn } from '@/lib/utils';

interface StatusBannerProps {
  status: ExtractionStatus;
  fileName: string;
  onCancel?: () => void;
}

const ACTIVE: ExtractionStatus[] = ['PENDING', 'PROCESSING'];

export function StatusBanner({ status, fileName, onCancel }: StatusBannerProps) {
  const { t } = useTranslation();
  const isActive = ACTIVE.includes(status);
  const isError = status === 'FAILED';
  const isCancelled = status === 'CANCELLED';

  return (
    <section
      aria-live="polite"
      aria-busy={isActive}
      className={cn(
        'flex items-center justify-between gap-3 rounded-xl border p-4',
        isActive && 'border-primary/30 bg-primary/5',
        status === 'COMPLETED' && 'border-success/30 bg-success/5',
        isError && 'border-destructive/30 bg-destructive/5',
        isCancelled && 'border-border bg-muted/40',
      )}
    >
      <div className="flex items-center gap-3 min-w-0">
        {isActive && <Loader2 className="h-5 w-5 shrink-0 animate-spin text-primary" />}
        {status === 'COMPLETED' && <CheckCircle2 className="h-5 w-5 shrink-0 text-success" />}
        {isError && <AlertTriangle className="h-5 w-5 shrink-0 text-destructive" />}
        {isCancelled && <Ban className="h-5 w-5 shrink-0 text-muted-foreground" />}
        {status === 'PENDING' && !isActive && <Clock className="h-5 w-5 shrink-0 text-primary" />}
        <div className="min-w-0">
          <p className="text-sm font-medium truncate">{fileName}</p>
          <p className="text-xs text-muted-foreground">
            {isActive ? t('status.polling') : t(`status.${status}`)}
          </p>
        </div>
      </div>

      {isActive && onCancel && (
        <Button variant="outline" size="sm" onClick={onCancel}>
          <X className="h-3.5 w-3.5 me-1.5" />
          {t('status.cancel')}
        </Button>
      )}
    </section>
  );
}
