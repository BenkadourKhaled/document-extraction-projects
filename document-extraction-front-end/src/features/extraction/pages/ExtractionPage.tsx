import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ArrowRight,
  FileCheck2,
  FileUp,
  Languages,
  Loader2,
  LockKeyhole,
  Play,
  ScanText,
  Sparkles,
  ShieldCheck,
  Zap,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable';
import { Dropzone } from '@/features/extraction/components/Dropzone';
import { OcrLanguagePicker } from '@/features/extraction/components/OcrLanguagePicker';
import { StatusBanner } from '@/features/extraction/components/StatusBanner';
import { ErrorDisplay } from '@/features/extraction/components/ErrorDisplay';
import { DocumentPreview } from '@/features/extraction/components/DocumentPreview';
import { ResultTextPanel } from '@/features/extraction/components/ResultTextPanel';
import { HistoryPanel } from '@/features/extraction/components/HistoryPanel';
import { useUploadExtraction } from '@/features/extraction/hooks/useUploadExtraction';
import { useExtractionStatus } from '@/features/extraction/hooks/useExtractionStatus';
import { useExtractionHistory } from '@/features/extraction/hooks/useExtractionHistory';
import { useDocumentFile } from '@/features/extraction/hooks/useDocumentFile';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { createExtractions, deleteExtraction } from '@/api/extractionService';
import { normalizeError } from '@/api/errors';
import type { ApiError, OcrLanguage } from '@/api/types';
import { loadLanguages, saveLanguages } from '@/utils/languageStorage';
import { cn } from '@/lib/utils';

type Phase = 'idle' | 'uploading' | 'processing' | 'done' | 'error';

export function ExtractionPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const params = useParams<{ id?: string }>();

  const [files, setFiles] = useState<File[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [languages, setLanguages] = useState<OcrLanguage[]>(() => loadLanguages());
  const [phase, setPhase] = useState<Phase>('idle');
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const activeId = params.id ?? null;
  const filesRef = useRef<File[]>([]);
  const lastUploadedIdRef = useRef<string | null>(null);

  const uploadMutation = useUploadExtraction();
  const statusQuery = useExtractionStatus(activeId);
  const history = useExtractionHistory();
  const isNarrow = useMediaQuery('(max-width: 767px)');

  const serverFile = useDocumentFile(
    activeId,
    !file && files.length === 0 && Boolean(statusQuery.data),
    statusQuery.data?.fileName,
    statusQuery.data?.mimeType,
  );
  const previewFile = file ?? serverFile.file;

  const status = statusQuery.data?.status;
  const statusFailure: ApiError | null =
    status === 'FAILED' || status === 'CANCELLED'
      ? {
          status: status === 'CANCELLED' ? 409 : 422,
          detail:
            statusQuery.data?.error ??
            (status === 'CANCELLED' ? t('status.CANCELLED') : t('errors.unknown')),
          isNetwork: false,
          isPasswordProtected: false,
          isTooLarge: false,
          raw: null,
        }
      : null;
  const displayedError = apiError ?? statusFailure;

  // The header "New extraction" link goes back to "/". Since this component is
  // reused between "/" and "/extraction/:id", clear the current document and
  // result when the route id disappears. When the route id is a result opened
  // from history, no local File object is available, so show the preview
  // placeholder instead of the previous document.
  useEffect(() => {
    if (!params.id) {
      lastUploadedIdRef.current = null;
      setFile(null);
      setFiles([]);
      filesRef.current = [];
      setApiError(null);
      setPhase('idle');
      return;
    }

    if (lastUploadedIdRef.current !== params.id) {
      setFile(null);
      setFiles([]);
      filesRef.current = [];
      setApiError(null);
      setPhase('idle');
    }
  }, [params.id]);

  // Drive phase from status
  useEffect(() => {
    if (!status) return;
    if (status === 'PENDING' || status === 'PROCESSING') setPhase('processing');
    else if (status === 'COMPLETED') setPhase('done');
    else if (status === 'FAILED' || status === 'CANCELLED') setPhase('error');
  }, [status]);

  function handleFileAccepted(f: File | File[]) {
    if (Array.isArray(f)) {
      setFiles(f);
      filesRef.current = f;
      // The dropzone calls this callback with an array when `multiple` is set,
      // even for a single file. Keep `file` in sync so the submit button and
      // the local preview work for the common single-file case.
      setFile(f[0] ?? null);
    } else {
      setFiles([f]);
      filesRef.current = [f];
      setFile(f);
    }
    setApiError(null);
  }

  async function handleStart() {
    const toUpload = filesRef.current.length > 0 ? filesRef.current : file ? [file] : [];
    if (toUpload.length === 0) return;
    saveLanguages(languages);
    setApiError(null);
    setPhase('uploading');
    try {
      if (toUpload.length === 1) {
        const data = await uploadMutation.mutateAsync({ file: toUpload[0], languages });
        lastUploadedIdRef.current = data.id;
        setPhase('processing');
        navigate(`/extraction/${data.id}`);
        return;
      }
      const data = await createExtractions(toUpload, languages);
      const first = data[0];
      if (first) {
        lastUploadedIdRef.current = first.id;
        setPhase('processing');
        navigate(`/extraction/${first.id}`);
      } else {
        setPhase('idle');
      }
      history.refresh();
    } catch (err) {
      setApiError(normalizeError(err));
      setPhase('error');
    }
  }

  async function handleCancel() {
    if (!activeId) return;
    try {
      await deleteExtraction(activeId);
      history.refresh();
      reset();
    } catch (err) {
      const normalized = normalizeError(err);
      // The document may already have been deleted by another tab or session.
      if (normalized.status === 404) {
        history.refresh();
        reset();
        return;
      }
      setApiError(normalized);
      setPhase('error');
    }
  }

  function reset() {
    lastUploadedIdRef.current = null;
    setFile(null);
    setFiles([]);
    filesRef.current = [];
    setApiError(null);
    setPhase('idle');
    navigate('/');
  }

  const showHome = !activeId && (phase === 'idle' || phase === 'uploading');
  const showUploadError = !activeId && phase === 'error' && apiError !== null;
  const showResult = phase === 'done' && statusQuery.data?.status === 'COMPLETED';
  const showResultError = phase === 'error' && displayedError !== null;

  const featureCards = [
    {
      icon: ScanText,
      title: t('home.features.ocr.title'),
      description: t('home.features.ocr.description'),
    },
    {
      icon: Languages,
      title: t('home.features.languages.title'),
      description: t('home.features.languages.description'),
    },
    {
      icon: FileCheck2,
      title: t('home.features.limit.title'),
      description: t('home.features.limit.description'),
    },
  ];

  return (
    <div
      className={cn(
        'mx-auto flex min-h-0 w-full max-w-[1600px] flex-1 flex-col overflow-x-hidden p-4 hero-glow md:p-6 lg:p-8',
        activeId ? 'overflow-hidden' : 'overflow-y-auto lg:overflow-hidden',
      )}
    >
      {showHome && (
        <div className="grid min-w-0 animate-fade-in gap-5 lg:min-h-0 lg:flex-1 lg:grid-cols-[minmax(0,1.45fr)_minmax(340px,0.75fr)] lg:grid-rows-1 xl:gap-7">
          <section className="scrollbar-thin flex min-w-0 flex-col gap-5 lg:min-h-0 lg:overflow-y-auto lg:pe-2">
            <header className="shrink-0 animate-slide-up">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/[0.07] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-primary">
                  <Sparkles className="h-3.5 w-3.5" />
                  {t('home.badge')}
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-background/60 px-3 py-1.5 text-[11px] font-medium text-muted-foreground shadow-sm backdrop-blur">
                    <ShieldCheck className="h-3.5 w-3.5 text-success" />
                    {t('home.secure')}
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-background/60 px-3 py-1.5 text-[11px] font-medium text-muted-foreground shadow-sm backdrop-blur">
                    <Zap className="h-3.5 w-3.5 text-warning" />
                    {t('home.fast')}
                  </span>
                </div>
              </div>

              <h1 className="mt-4 max-w-4xl text-3xl font-semibold leading-[1.08] tracking-[-0.035em] sm:text-4xl xl:text-[2.85rem]">
                <span className="text-gradient">{t('app.title')}</span>
              </h1>
              <p className="mt-2.5 max-w-2xl text-sm leading-relaxed text-muted-foreground md:text-[15px]">
                {t('app.subtitle')}
              </p>
            </header>

            <div className="relative min-w-0 shrink-0 overflow-hidden rounded-[1.75rem] border border-border/80 bg-card/80 shadow-[0_24px_80px_-32px_hsl(var(--primary)/0.35)] backdrop-blur-xl">
              <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/50 to-transparent" />
              <div className="pointer-events-none absolute -end-24 -top-24 h-56 w-56 rounded-full bg-primary/10 blur-3xl" />

              <div className="relative p-4 sm:p-6 lg:p-7">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-primary to-sky-500 text-primary-foreground shadow-lg shadow-primary/20">
                      <FileUp className="h-5 w-5" />
                    </span>
                    <div>
                      <h2 className="text-base font-semibold tracking-tight">
                        {t('home.uploadTitle')}
                      </h2>
                      <p className="mt-1 max-w-xl text-xs leading-relaxed text-muted-foreground sm:text-[13px]">
                        {t('home.uploadDescription')}
                      </p>
                    </div>
                  </div>

                  <span className="hidden shrink-0 rounded-full border border-border/70 bg-background/70 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground sm:inline-flex">
                    PDF · IMAGE
                  </span>
                </div>

                <div className="mt-5 grid gap-4 lg:grid-cols-[minmax(0,1.15fr)_minmax(250px,0.85fr)]">
                  <Dropzone
                    onFileAccepted={handleFileAccepted}
                    disabled={uploadMutation.isPending || phase === 'uploading'}
                    multiple
                  />

                  <div className="min-h-0 rounded-2xl border border-border/70 bg-muted/25 p-4">
                    <OcrLanguagePicker value={languages} onChange={setLanguages} />
                  </div>
                </div>

                <div className="mt-4 flex flex-col-reverse gap-4 border-t border-border/70 pt-4 sm:flex-row sm:items-center sm:justify-between">
                  <p className="flex max-w-md items-start gap-2 text-[11px] leading-relaxed text-muted-foreground">
                    <LockKeyhole className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" />
                    {t('home.privacy')}
                  </p>

                  <Button
                    onClick={handleStart}
                    disabled={(!file && files.length === 0) || uploadMutation.isPending}
                    size="lg"
                    className="group h-12 w-full rounded-xl bg-gradient-to-r from-primary via-primary to-sky-500 px-6 text-sm font-semibold shadow-lg shadow-primary/20 transition-all hover:shadow-xl hover:shadow-primary/25 disabled:shadow-none sm:w-auto"
                  >
                    {uploadMutation.isPending ? (
                      <Loader2 className="me-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Play className="me-2 h-4 w-4" />
                    )}
                    {uploadMutation.isPending ? t('status.PROCESSING') : t('upload.start')}
                    {!uploadMutation.isPending && (
                      <ArrowRight className="ms-2 h-4 w-4 transition-transform group-hover:translate-x-0.5 rtl:rotate-180" />
                    )}
                  </Button>
                </div>
              </div>
            </div>

            <div className="grid shrink-0 gap-3 sm:grid-cols-3">
              {featureCards.map((feature) => {
                const Icon = feature.icon;
                return (
                  <div
                    key={feature.title}
                    className="group flex items-start gap-3 rounded-2xl border border-border/70 bg-card/60 p-3.5 shadow-sm backdrop-blur-sm transition-colors hover:border-primary/25 hover:bg-card/80"
                  >
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                      <Icon className="h-4 w-4" />
                    </span>
                    <div className="min-w-0">
                      <p className="text-[12.5px] font-semibold tracking-tight">{feature.title}</p>
                      <p className="mt-0.5 text-[10.5px] leading-relaxed text-muted-foreground">
                        {feature.description}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          <aside className="h-[470px] min-w-0 lg:h-full lg:min-h-0">
            <HistoryPanel
              history={history.history}
              total={history.total}
              query={history.query}
              status={history.status}
              onQueryChange={history.setQuery}
              onStatusChange={history.setStatus}
              isLoading={history.isLoading}
              isFetching={history.isFetching}
              hasMore={history.hasMore}
              isLoadingMore={history.isLoadingMore}
              onLoadMore={history.loadMore}
              onRemove={history.remove}
              onClear={history.clear}
            />
          </aside>
        </div>
      )}

      {activeId && (
        <div className="flex min-h-0 flex-1 flex-col gap-4">
          <StatusBanner
            status={statusQuery.data?.status ?? 'PENDING'}
            fileName={statusQuery.data?.fileName ?? file?.name ?? ''}
            onCancel={phase === 'processing' ? handleCancel : undefined}
          />

          {showResultError && displayedError && (
            <ErrorDisplay error={displayedError} onRetry={reset} />
          )}

          {statusQuery.error && !showResultError && (
            <ErrorDisplay error={normalizeError(statusQuery.error)} onRetry={reset} />
          )}

          {showResult && statusQuery.data && (
            <ResizablePanelGroup
              key={isNarrow ? 'vertical' : 'horizontal'}
              direction={isNarrow ? 'vertical' : 'horizontal'}
              className="min-h-0 flex-1 rounded-xl border"
            >
              <ResizablePanel defaultSize={45} minSize={30} className="min-h-0">
                {previewFile ? (
                  <DocumentPreview file={previewFile} />
                ) : serverFile.isLoading ? (
                  <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {t('loading')}
                  </div>
                ) : (
                  <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                    {t('result.preview')}
                  </div>
                )}
              </ResizablePanel>
              <ResizableHandle withHandle />
              <ResizablePanel defaultSize={55} minSize={30} className="min-h-0">
                <ResultTextPanel extraction={statusQuery.data} onNewExtraction={reset} />
              </ResizablePanel>
            </ResizablePanelGroup>
          )}

          {(phase === 'processing' || phase === 'uploading') &&
            !showResult &&
            !showResultError &&
            !statusQuery.error && (
              <div className="flex flex-1 items-center justify-center rounded-xl border bg-card/50 animate-fade-in">
                <div className="space-y-4 px-4 text-center">
                  <div className="mx-auto h-2 w-56 progress-indeterminate rounded-full" />
                  <p className="text-sm text-muted-foreground">{t('status.polling')}</p>
                </div>
              </div>
            )}
        </div>
      )}

      {showUploadError && apiError && (
        <section className="mx-auto flex w-full max-w-2xl flex-1 flex-col justify-center py-6 animate-slide-up">
          <ErrorDisplay error={apiError} onRetry={reset} />
        </section>
      )}
    </div>
  );
}
