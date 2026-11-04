import { useEffect, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import { useTranslation } from 'react-i18next';
import {
  ZoomIn,
  ZoomOut,
  Maximize,
  ChevronLeft,
  ChevronRight,
  Download,
  ImageOff,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { isPdf, isTiff } from '@/utils/fileValidation';
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.js?url';

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorker;

interface DocumentPreviewProps {
  file: File;
}

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.25;
const PDF_OPTIONS = { isEvalSupported: false };

export function DocumentPreview({ file }: DocumentPreviewProps) {
  const { t } = useTranslation();
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [numPages, setNumPages] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [zoom, setZoom] = useState<number>(1);
  const pdf = isPdf(file);
  const tiff = isTiff(file);

  useEffect(() => {
    const url = URL.createObjectURL(file);
    setObjectUrl(url);
    return () => {
      URL.revokeObjectURL(url);
    };
  }, [file]);

  useEffect(() => {
    setPage(1);
    setNumPages(0);
  }, [file]);

  if (!objectUrl) return null;

  return (
    <div className="flex h-full flex-col bg-muted/30">
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <p className="text-sm font-medium truncate">{file.name}</p>
        {!tiff && (
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" onClick={() => setZoom((z) => Math.max(MIN_ZOOM, z - ZOOM_STEP))} aria-label={t('result.zoomOut')}>
              <ZoomOut className="h-4 w-4" />
            </Button>
            <span className="w-12 text-center text-xs tabular-nums">{Math.round(zoom * 100)}%</span>
            <Button variant="ghost" size="icon" onClick={() => setZoom((z) => Math.min(MAX_ZOOM, z + ZOOM_STEP))} aria-label={t('result.zoomIn')}>
              <ZoomIn className="h-4 w-4" />
            </Button>
            <Button variant="ghost" size="icon" onClick={() => setZoom(1)} aria-label={t('result.zoomReset')}>
              <Maximize className="h-4 w-4" />
            </Button>
          </div>
        )}
      </div>

      <div className="relative flex-1 overflow-auto scrollbar-thin">
        <div className="flex min-h-full items-start justify-center p-4">
          {tiff ? (
            <div className="flex max-w-md flex-col items-center gap-3 rounded-lg border border-dashed bg-background px-6 py-8 text-center">
              <ImageOff className="h-10 w-10 text-muted-foreground" aria-hidden="true" />
              <p className="text-sm text-muted-foreground">
                {t('result.tiffPreviewUnavailable')}
              </p>
              <Button asChild variant="outline" size="sm">
                <a href={objectUrl} download={file.name}>
                  <Download className="me-2 h-4 w-4" aria-hidden="true" />
                  {t('result.downloadOriginal')}
                </a>
              </Button>
            </div>
          ) : pdf ? (
            <Document
              file={objectUrl}
              onLoadSuccess={({ numPages: n }) => setNumPages(n)}
              loading={<span className="text-sm text-muted-foreground">{t('loading')}</span>}
              options={PDF_OPTIONS}
              className={cn('mx-auto')}
            >
              <Page
                pageNumber={page}
                scale={zoom}
                renderTextLayer={false}
                renderAnnotationLayer={false}
              />
            </Document>
          ) : (
            <img
              src={objectUrl}
              alt={t('result.preview')}
              className="max-w-full select-none rounded-lg shadow-sm"
              style={{ transform: `scale(${zoom})`, transformOrigin: 'top center' }}
            />
          )}
        </div>
      </div>

      {pdf && numPages > 0 && (
        <div className="flex items-center justify-center gap-2 border-t px-3 py-2">
          <Button variant="ghost" size="icon" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))} aria-label={t('result.prevPage')}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-xs tabular-nums">{page} / {numPages}</span>
          <Button variant="ghost" size="icon" disabled={page >= numPages} onClick={() => setPage((p) => Math.min(numPages, p + 1))} aria-label={t('result.nextPage')}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
