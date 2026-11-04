import { useCallback, useState } from 'react';
import { useDropzone, type FileRejection } from 'react-dropzone';
import { useTranslation } from 'react-i18next';
import { AlertCircle, FileText, ImageIcon, RotateCcw, UploadCloud } from 'lucide-react';
import { cn } from '@/lib/utils';
import { validateFile, isPdf } from '@/utils/fileValidation';

interface DropzoneProps {
  onFileAccepted: (file: File | File[]) => void;
  disabled?: boolean;
  multiple?: boolean;
}

function formatFileSize(bytes: number, locale: string): string {
  const megabytes = bytes / (1024 * 1024);
  const useMegabytes = megabytes >= 0.1;
  const value = useMegabytes ? megabytes : bytes / 1024;
  const unit: 'megabyte' | 'kilobyte' = useMegabytes ? 'megabyte' : 'kilobyte';

  try {
    return new Intl.NumberFormat(locale, {
      style: 'unit',
      unit,
      unitDisplay: 'short',
      maximumFractionDigits: 1,
    }).format(value);
  } catch {
    return `${value.toFixed(1)} ${useMegabytes ? 'MB' : 'KB'}`;
  }
}

export function Dropzone({ onFileAccepted, disabled = false, multiple = false }: DropzoneProps) {
  const { t, i18n } = useTranslation();
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<{
    name: string;
    type: 'pdf' | 'image';
    size: number;
    count?: number;
  } | null>(null);

  const onDrop = useCallback(
    (accepted: File[], rejections: FileRejection[]) => {
      setError(null);
      if (rejections.length > 0) {
        setError(t('upload.invalidType'));
        return;
      }
      if (accepted.length === 0) return;
      const filesToValidate = multiple ? accepted : [accepted[0]];
      for (const file of filesToValidate) {
        const result = validateFile(file);
        if (!result.valid) {
          setError(result.error === 'too-large' ? t('upload.tooLarge') : t('upload.invalidType'));
          setPreview(null);
          return;
        }
      }
      if (multiple) {
        const first = filesToValidate[0];
        setPreview({
          name: first.name,
          type: isPdf(first) ? 'pdf' : 'image',
          size: first.size,
          count: filesToValidate.length,
        });
        onFileAccepted(filesToValidate);
      } else {
        const file = filesToValidate[0];
        setPreview({
          name: file.name,
          type: isPdf(file) ? 'pdf' : 'image',
          size: file.size,
        });
        onFileAccepted(file);
      }
    },
    [multiple, onFileAccepted, t],
  );

  const { getRootProps, getInputProps, isDragActive, isDragReject } = useDropzone({
    onDrop,
    accept: {
      'application/pdf': ['.pdf'],
      'image/png': ['.png'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/tiff': ['.tif', '.tiff'],
      'image/bmp': ['.bmp'],
      'image/webp': ['.webp'],
    },
    maxFiles: 1,
    disabled,
    noKeyboard: false,
  });

  return (
    <div className="flex h-full min-w-0 flex-col gap-3">
      <div
        {...getRootProps()}
        className={cn(
          'group relative flex min-h-[250px] flex-1 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed px-5 py-7 text-center transition-all duration-200',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
          isDragActive &&
            !isDragReject &&
            'border-primary bg-primary/[0.06] shadow-[inset_0_0_0_1px_hsl(var(--primary)/0.15)]',
          isDragReject && 'border-destructive bg-destructive/5',
          !isDragActive &&
            'border-border/90 bg-gradient-to-b from-muted/30 to-background/10 hover:border-primary/50 hover:bg-accent/20',
          disabled && 'pointer-events-none opacity-60',
        )}
        role="button"
        tabIndex={0}
        aria-label={t('upload.title') as string}
      >
        <input {...getInputProps()} aria-label={t('upload.button') as string} />

        <div
          className={cn(
            'pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-200',
            isDragActive && 'opacity-100',
          )}
        >
          <div className="absolute -top-16 start-1/2 h-44 w-72 -translate-x-1/2 rounded-full bg-primary/15 blur-3xl rtl:translate-x-1/2" />
        </div>

        <div className="relative mb-4">
          <div className="absolute inset-0 rounded-2xl bg-primary/20 blur-xl transition-opacity group-hover:opacity-100" />
          <div
            className={cn(
              'relative grid h-16 w-16 place-items-center rounded-2xl border border-border/70 bg-background/90 text-primary shadow-sm transition-transform duration-200 group-hover:scale-105',
              isDragActive && 'scale-105 border-primary/30',
            )}
          >
            {preview?.type === 'image' ? (
              <ImageIcon className="h-7 w-7" />
            ) : preview?.type === 'pdf' ? (
              <FileText className="h-7 w-7" />
            ) : (
              <UploadCloud className="h-7 w-7" />
            )}
          </div>
        </div>

        {preview ? (
          <>
            <p className="max-w-full truncate text-base font-semibold tracking-tight">
              {preview.name}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {formatFileSize(preview.size, i18n.language)} · {t('upload.ready')}
            </p>
            <span className="mt-3 inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-background/70 px-3 py-1 text-[11px] font-medium text-muted-foreground transition-colors group-hover:text-primary">
              <RotateCcw className="h-3 w-3" />
              {t('upload.change')}
            </span>
          </>
        ) : (
          <>
            <p className="text-lg font-semibold tracking-tight">{t('upload.title')}</p>
            <p className="mt-1 text-sm text-muted-foreground">{t('upload.hint')}</p>
            <span className="mt-4 inline-flex h-9 items-center rounded-lg bg-primary px-4 text-xs font-semibold text-primary-foreground shadow-sm transition-transform duration-200 group-hover:-translate-y-0.5">
              {t('upload.button')}
            </span>
          </>
        )}

        <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
          <span className="rounded-md border border-border/60 bg-background/60 px-2 py-1 text-[10px] font-medium text-muted-foreground">
            {t('upload.formats')}
          </span>
          <span className="rounded-md border border-border/60 bg-background/60 px-2 py-1 text-[10px] font-medium text-muted-foreground">
            {t('upload.maxSize')}
          </span>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}

