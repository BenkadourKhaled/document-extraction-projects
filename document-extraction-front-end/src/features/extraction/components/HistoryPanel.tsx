import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  AlertTriangle,
  Archive,
  Ban,
  CheckCircle2,
  ChevronRight,
  Clock,
  FileText,
  Filter,
  History,
  Image as ImageIcon,
  Loader2,
  LockKeyhole,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { cn } from '@/lib/utils';
import type { HistoryEntry, ExtractionStatus } from '@/api/types';
import { downloadDocumentsZip } from '@/api/extractionService';
import { toast } from 'sonner';

interface HistoryPanelProps {
  history: HistoryEntry[];
  total: number;
  query: string;
  status: ExtractionStatus | '';
  onQueryChange: (value: string) => void;
  onStatusChange: (value: ExtractionStatus | '') => void;
  isLoading?: boolean;
  isFetching?: boolean;
  hasMore?: boolean;
  isLoadingMore?: boolean;
  onLoadMore?: () => void;
  onRemove: (id: string) => void;
  onClear: () => void;
}

const STATUS_CONFIG: Record<
  ExtractionStatus,
  { icon: React.ReactNode; color: string; badge: string; label: string }
> = {
  PENDING: {
    icon: <Clock className="h-3 w-3" />,
    color: 'text-muted-foreground',
    badge: 'border-border bg-muted/60 text-muted-foreground',
    label: 'status.PENDING',
  },
  PROCESSING: {
    icon: <Loader2 className="h-3 w-3 animate-spin" />,
    color: 'text-primary',
    badge: 'border-primary/20 bg-primary/10 text-primary',
    label: 'status.PROCESSING',
  },
  COMPLETED: {
    icon: <CheckCircle2 className="h-3 w-3" />,
    color: 'text-success',
    badge: 'border-success/20 bg-success/10 text-success',
    label: 'status.COMPLETED',
  },
  FAILED: {
    icon: <AlertTriangle className="h-3 w-3" />,
    color: 'text-destructive',
    badge: 'border-destructive/20 bg-destructive/10 text-destructive',
    label: 'status.FAILED',
  },
  CANCELLED: {
    icon: <Ban className="h-3 w-3" />,
    color: 'text-muted-foreground',
    badge: 'border-border bg-muted/60 text-muted-foreground',
    label: 'status.CANCELLED',
  },
};

const HISTORY_STATUSES: ExtractionStatus[] = [
  'PENDING',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
];

function isPdfFile(fileName: string): boolean {
  return fileName.toLowerCase().endsWith('.pdf');
}

function formatDate(value: string, locale: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  try {
    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  } catch {
    return date.toLocaleString();
  }
}

export function HistoryPanel({
  history,
  total,
  query,
  status,
  onQueryChange,
  onStatusChange,
  isLoading = false,
  isFetching = false,
  hasMore = false,
  isLoadingMore = false,
  onLoadMore,
  onRemove,
  onClear,
}: HistoryPanelProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [deleteTarget, setDeleteTarget] = useState<HistoryEntry | null>(null);
  const [clearOpen, setClearOpen] = useState(false);
  const hasFilters = query.trim().length > 0 || status !== '';

  const confirmDelete = () => {
    const id = deleteTarget?.id;
    setDeleteTarget(null);
    if (id) onRemove(id);
  };

  const confirmClear = () => {
    setClearOpen(false);
    onClear();
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-[1.75rem] border border-border/80 bg-card/80 shadow-[0_24px_80px_-40px_hsl(var(--primary)/0.35)] backdrop-blur-xl">
      <div className="flex shrink-0 items-start justify-between gap-3 border-b border-border/70 px-5 py-4">
        <div className="flex min-w-0 items-center gap-3">
          <span className="relative grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/15">
            <History className="h-[18px] w-[18px]" />
            <span className="absolute -end-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-card bg-success" />
          </span>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h2 className="truncate text-sm font-semibold tracking-tight">{t('history.title')}</h2>
              {total > 0 && (
                <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary tabular-nums">
                  {total > 99 ? '99+' : total}
                </span>
              )}
              {isFetching && !isLoading && (
                <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" aria-hidden="true" />
              )}
            </div>
            <p className="mt-0.5 truncate text-[11px] text-muted-foreground">
              {t('history.subtitle')}
            </p>
          </div>
        </div>

        {total > 0 && (
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setClearOpen(true)}
            className="-me-2 h-8 w-8 shrink-0 rounded-lg text-muted-foreground hover:text-destructive"
            aria-label={t('history.clear') as string}
            title={t('history.clear') as string}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        )}
      </div>

      <div className="shrink-0 border-b border-border/70 px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="relative min-w-0 flex-1">
            <Search
              className="pointer-events-none absolute start-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              value={query}
              onChange={(event) => onQueryChange(event.target.value)}
              placeholder={t('history.searchPlaceholder')}
              aria-label={t('history.searchPlaceholder')}
              className="h-9 rounded-xl bg-background/60 ps-9 pe-8 text-xs"
            />
            {query.length > 0 && (
              <button
                type="button"
                onClick={() => onQueryChange('')}
                aria-label={t('history.clearSearch')}
                className="absolute end-2 top-1/2 grid h-5 w-5 -translate-y-1/2 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
          <Select
            value={status || 'ALL'}
            onValueChange={(value) =>
              onStatusChange(value === 'ALL' ? '' : (value as ExtractionStatus))
            }
          >
            <SelectTrigger
              className="h-9 w-[8.75rem] shrink-0 rounded-xl bg-background/60 text-xs"
              aria-label={t('history.filterAll')}
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{t('history.filterAll')}</SelectItem>
              {HISTORY_STATUSES.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`status.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="relative min-h-0 flex-1">
        {isLoading ? (
          <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
            <Loader2 className="h-5 w-5 animate-spin text-primary" />
            <p className="text-xs text-muted-foreground">{t('history.loading')}</p>
          </div>
        ) : history.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 p-6 text-center">
            <div className="relative">
              <div className="absolute inset-0 rounded-3xl bg-primary/10 blur-xl" />
              <span className="relative grid h-14 w-14 place-items-center rounded-2xl border border-border/70 bg-background/80 text-muted-foreground shadow-sm">
                {hasFilters ? <Search className="h-6 w-6" /> : <History className="h-6 w-6" />}
              </span>
            </div>
            <div>
              <p className="text-sm font-semibold">
                {t(hasFilters ? 'history.noResultsTitle' : 'history.emptyTitle')}
              </p>
              <p className="mx-auto mt-1 max-w-[230px] text-xs leading-relaxed text-muted-foreground">
                {t(hasFilters ? 'history.noResultsHint' : 'history.emptyHint')}
              </p>
            </div>
            {hasFilters && (
              <Button
                variant="outline"
                size="sm"
                className="h-8 rounded-lg text-xs"
                onClick={() => {
                  onQueryChange('');
                  onStatusChange('');
                }}
              >
                {t('history.resetFilters')}
              </Button>
            )}
          </div>
        ) : (
          <ScrollArea className="h-full" role="region" aria-label={t('history.title') as string}>
            <ul className="space-y-1 p-2.5">
              {history.map((entry, i) => {
                const cfg = STATUS_CONFIG[entry.status];
                const pdf = isPdfFile(entry.fileName);
                return (
                  <li
                    key={entry.id}
                    className="group animate-fade-in"
                    style={{ animationDelay: `${i * 35}ms` }}
                  >
                    <div className="flex items-start gap-1 rounded-xl border border-transparent p-1 transition-colors hover:border-border/70 hover:bg-accent/40">
                      <button
                        type="button"
                        onClick={() => navigate(`/extraction/${entry.id}`)}
                        className="flex min-w-0 flex-1 items-start gap-3 rounded-lg p-2 text-start focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                        aria-label={`${t('history.reopen')} ${entry.fileName}, ${t(cfg.label)}`}
                      >
                        <span
                          className={cn(
                            'mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-xl border bg-background/70',
                            pdf
                              ? 'border-rose-500/15 text-rose-500'
                              : 'border-primary/15 text-primary',
                          )}
                        >
                          {pdf ? (
                            <FileText className="h-4 w-4" />
                          ) : (
                            <ImageIcon className="h-4 w-4" />
                          )}
                        </span>

                        <span className="min-w-0 flex-1">
                          <span className="flex items-center justify-between gap-2">
                            <span
                              className="truncate text-[13px] font-medium"
                              title={entry.fileName}
                            >
                              {entry.fileName}
                            </span>
                            <span
                              className={cn(
                                'inline-flex shrink-0 items-center rounded-full border px-1.5 py-0.5',
                                cfg.badge,
                              )}
                              title={t(cfg.label)}
                            >
                              {cfg.icon}
                              <span className="sr-only">{t(cfg.label)}</span>
                            </span>
                          </span>
                          <span className="mt-1 flex items-center gap-1.5 text-[11px] text-muted-foreground">
                            <Clock className="h-3 w-3" />
                            {formatDate(entry.date, i18n.language)}
                          </span>
                        </span>

                        <ChevronRight className="mt-2 h-3.5 w-3.5 shrink-0 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5 rtl:rotate-180" />
                      </button>

                      <Button
                        variant="ghost"
                        size="icon"
                        className="mt-1 h-8 w-8 shrink-0 rounded-lg text-muted-foreground opacity-100 hover:text-destructive focus-visible:opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
                        onClick={() => setDeleteTarget(entry)}
                        aria-label={`${t('history.delete')} ${entry.fileName}`}
                        title={t('history.delete') as string}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </li>
                );
              })}
            </ul>
            {hasMore && onLoadMore && (
              <div className="px-2.5 pb-3">
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full rounded-xl text-xs"
                  onClick={onLoadMore}
                  disabled={isLoadingMore}
                >
                  {isLoadingMore ? (
                    <Loader2 className="me-2 h-3.5 w-3.5 animate-spin" />
                  ) : null}
                  {isLoadingMore ? t('history.loadingMore') : t('history.loadMore')}
                </Button>
              </div>
            )}
          </ScrollArea>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-1.5 border-t border-border/70 px-4 py-2.5 text-[10px] text-muted-foreground">
        <LockKeyhole className="h-3 w-3 text-success" />
        <span className="truncate">{t('history.secure')}</span>
      </div>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('history.deleteTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('history.deleteConfirm')}
              {deleteTarget ? (
                <span className="mt-2 block truncate font-medium text-foreground">
                  {deleteTarget.fileName}
                </span>
              ) : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('history.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {t('history.deleteAction')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={clearOpen} onOpenChange={setClearOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('history.clearTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{t('history.clearConfirm')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('history.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmClear}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {t('history.clearAction')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}


