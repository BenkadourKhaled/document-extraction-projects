import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Copy,
  Check,
  FileJson,
  FileText,
  Loader2,
  Search,
  Plus,
  ShieldCheck,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { FieldPanel } from '@/features/extraction/components/FieldPanel';
import { cn } from '@/lib/utils';
import type {
  ExtractedField,
  Extraction,
  ExtractionMethod,
  FieldSource,
} from '@/api/types';
import { normalizeError } from '@/api/errors';
import { useCopyToClipboard } from '@/utils/useCopyToClipboard';
import { downloadServerExtraction } from '@/utils/download';
import { findMatches, buildSegments } from '@/utils/search';

interface ResultTextPanelProps {
  extraction: Extraction;
  onNewExtraction: () => void;
  selectedFieldKey?: string | null;
  onSourceSelect?: (field: ExtractedField, source: FieldSource | null) => void;
}

export function ResultTextPanel({
  extraction,
  onNewExtraction,
  selectedFieldKey,
  onSourceSelect,
}: ResultTextPanelProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [downloading, setDownloading] = useState<'txt' | 'json' | null>(null);
  const [fullText, setFullText] = useState(extraction.fullText);
  const [pageTexts, setPageTexts] = useState<Record<number, string>>(
    Object.fromEntries(extraction.pages.map((p) => [p.number, p.text])),
  );
  const [copied, copy] = useCopyToClipboard();

  // Re-sync when a new extraction comes in.
  useEffect(() => {
    setFullText(extraction.fullText);
    setPageTexts(Object.fromEntries(extraction.pages.map((p) => [p.number, p.text])));
  }, [extraction.id, extraction.fullText, extraction.pages]);

  const matches = useMemo(() => findMatches(fullText, query), [fullText, query]);
  const segments = useMemo(() => buildSegments(fullText, matches), [fullText, matches]);

  function handleCopy() {
    void copy(fullText);
  }

  async function handleDownload(format: 'txt' | 'json') {
    setDownloading(format);
    try {
      // The server export is authoritative: it contains the effective field
      // values (including corrections), unlike a locally rebuilt payload.
      await downloadServerExtraction(extraction.id, extraction.fileName, format);
    } catch (error) {
      toast.error(normalizeError(error).detail);
    } finally {
      setDownloading(null);
    }
  }

  function updatePageText(number: number, text: string) {
    setPageTexts((prev) => {
      const next = { ...prev, [number]: text };
      setFullText(Object.values(next).join('\n\n'));
      return next;
    });
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
        <div className="flex items-center gap-2 text-sm">
          <span className="font-medium">{t('result.title')}</span>
          {extraction.pageCount > 0 && (
            <Badge variant="secondary" className="font-normal">
              {extraction.pageCount} {t('result.pages')}
            </Badge>
          )}
          {extraction.durationMs != null && (
            <Badge variant="outline" className="font-normal">
              {t('result.duration')}: {(extraction.durationMs / 1000).toFixed(1)}s
            </Badge>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" onClick={handleCopy}>
            {copied.copied ? <Check className="h-3.5 w-3.5 me-1.5 text-success" /> : <Copy className="h-3.5 w-3.5 me-1.5" />}
            {copied.copied ? t('result.copied') : t('result.copy')}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleDownload('txt')}
            disabled={downloading !== null}
          >
            {downloading === 'txt' ? (
              <Loader2 className="h-3.5 w-3.5 me-1.5 animate-spin" />
            ) : (
              <FileText className="h-3.5 w-3.5 me-1.5" />
            )}
            .txt
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleDownload('json')}
            disabled={downloading !== null}
          >
            {downloading === 'json' ? (
              <Loader2 className="h-3.5 w-3.5 me-1.5 animate-spin" />
            ) : (
              <FileJson className="h-3.5 w-3.5 me-1.5" />
            )}
            .json
          </Button>
          <Button size="sm" onClick={onNewExtraction}>
            <Plus className="h-3.5 w-3.5 me-1.5" />
            {t('result.newExtraction')}
          </Button>
        </div>
      </div>

      <div className="px-4 pt-3">
        <div className="relative">
          <Search className="absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
          <Input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('result.searchPlaceholder')}
            className="ps-9"
            aria-label={t('result.search')}
          />
        </div>
        {query && (
          <p className="mt-1.5 text-xs text-muted-foreground">
            {matches.length > 0 ? t('result.matchCount', { count: matches.length }) : t('result.noResults')}
          </p>
        )}
      </div>

      <Tabs defaultValue="full" className="flex min-h-0 flex-1 flex-col px-4 pb-4 pt-3">
        <TabsList>
          <TabsTrigger value="full">{t('result.fullText')}</TabsTrigger>
          <TabsTrigger value="pages">{t('result.byPage')}</TabsTrigger>
          <TabsTrigger value="fields" className="gap-1.5">
            <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
            {t('fields.tab')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="full" className="mt-2 min-h-0 flex-1">
          <ScrollArea className="h-full rounded-lg border">
            <textarea
              aria-label={t('result.editable')}
              value={fullText}
              onChange={(e) => setFullText(e.target.value)}
              dir="auto"
              className="min-h-full w-full resize-none bg-transparent p-4 font-mono text-sm leading-relaxed outline-none scrollbar-thin"
              spellCheck={false}
            />
          </ScrollArea>
          {query && (
            <div className="mt-2 max-h-24 overflow-auto scrollbar-thin rounded-lg bg-muted/40 p-3 text-sm whitespace-pre-wrap">
              {segments.map((seg, i) =>
                seg.match ? (
                  <mark key={i} className="search-highlight">{seg.text}</mark>
                ) : (
                  <span key={i}>{seg.text}</span>
                ),
              )}
            </div>
          )}
        </TabsContent>

        <TabsContent value="pages" className="mt-2 min-h-0 flex-1 overflow-auto scrollbar-thin">
          <div className="space-y-4">
            {extraction.pages.map((p) => (
              <PageBlock
                key={p.number}
                number={p.number}
                method={p.method}
                confidence={p.confidence}
                text={pageTexts[p.number] ?? p.text}
                query={query}
                onChange={(text) => updatePageText(p.number, text)}
              />
            ))}
          </div>
        </TabsContent>

        <TabsContent value="fields" className="mt-2 min-h-0 flex-1">
          <FieldPanel
            extractionId={extraction.id}
            selectedFieldKey={selectedFieldKey}
            onSourceSelect={onSourceSelect ?? (() => undefined)}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}

interface PageBlockProps {
  number: number;
  method: ExtractionMethod;
  confidence: number | null;
  text: string;
  query: string;
  onChange: (text: string) => void;
}

function PageBlock({ number, method, confidence, text, query, onChange }: PageBlockProps) {
  const { t } = useTranslation();
  const matches = useMemo(() => findMatches(text, query), [text, query]);
  const segments = useMemo(() => buildSegments(text, matches), [text, matches]);
  const isOcr = method === 'OCR';

  return (
    <div className="rounded-lg border">
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <span className="text-sm font-medium">#{number}</span>
        <div className="flex items-center gap-2">
          <Badge variant={isOcr ? 'default' : 'secondary'} className={cn('font-normal', !isOcr && 'bg-muted')}>
            {t(`result.method.${method}`)}
          </Badge>
          {isOcr && confidence != null && (
            <Badge variant="outline" className="font-normal">
              {t('result.confidence')}: {Math.round(confidence * 100)}%
            </Badge>
          )}
        </div>
      </div>
      <textarea
        value={text}
        onChange={(e) => onChange(e.target.value)}
        dir="auto"
        className="min-h-[120px] w-full resize-y bg-transparent p-3 font-mono text-sm leading-relaxed outline-none scrollbar-thin"
        spellCheck={false}
        aria-label={`${t('result.byPage')} ${number}`}
      />
      {query && matches.length > 0 && (
        <div className="border-t px-3 py-2 text-xs text-muted-foreground whitespace-pre-wrap">
          {segments.map((seg, i) =>
            seg.match ? <mark key={i} className="search-highlight">{seg.text}</mark> : <span key={i}>{seg.text}</span>,
          )}
        </div>
      )}
    </div>
  );
}
