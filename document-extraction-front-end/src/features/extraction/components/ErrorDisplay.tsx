import { useTranslation } from 'react-i18next';
import { AlertCircle, RefreshCw, Lock, FileWarning } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { ApiError } from '@/api/types';

interface ErrorDisplayProps {
  error: ApiError;
  onRetry?: () => void;
}

export function ErrorDisplay({ error, onRetry }: ErrorDisplayProps) {
  const { t } = useTranslation();

  let message: string;
  let icon: React.ReactNode;

  if (error.isPasswordProtected) {
    message = t('errors.passwordProtected');
    icon = <Lock className="h-5 w-5 mt-0.5 shrink-0" />;
  } else if (error.isTooLarge) {
    message = t('upload.tooLarge');
    icon = <FileWarning className="h-5 w-5 mt-0.5 shrink-0" />;
  } else if (error.isNetwork) {
    message = t('errors.network');
    icon = <AlertCircle className="h-5 w-5 mt-0.5 shrink-0" />;
  } else {
    message = t('errors.api', { detail: error.detail });
    icon = <AlertCircle className="h-5 w-5 mt-0.5 shrink-0" />;
  }

  return (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-5"
    >
      <div className="flex items-start gap-3">
        <span className="text-destructive">{icon}</span>
        <div>
          <p className="font-medium text-destructive">{t('errors.title')}</p>
          <p className="text-sm text-destructive/90 mt-1">{message}</p>
        </div>
      </div>
      {onRetry && (
        <Button variant="outline" size="sm" className="w-fit" onClick={onRetry}>
          <RefreshCw className="h-3.5 w-3.5 me-2" />
          {t('errors.retry')}
        </Button>
      )}
    </div>
  );
}
