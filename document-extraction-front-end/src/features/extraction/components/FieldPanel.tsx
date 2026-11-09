import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CheckCircle2,
  CircleHelp,
  History,
  Loader2,
  PencilLine,
  ScanLine,
} from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Textarea } from '@/components/ui/textarea';
import { correctExtractionField, getExtractionFields } from '@/api/fieldService';
import { normalizeError } from '@/api/errors';
import type {
  ExtractedField,
  FieldCorrectionInput,
  FieldSource,
  FieldVerificationStatus,
} from '@/api/types';
import { cn } from '@/lib/utils';

interface FieldPanelProps {
  extractionId: string;
  selectedFieldKey?: string | null;
  onSourceSelect: (field: ExtractedField, source: FieldSource | null) => void;
}

const STATUS_STYLES: Record<FieldVerificationStatus, string> = {
  VERIFIED: 'border-success/30 bg-success/10 text-success',
  UNVERIFIED: 'border-warning/30 bg-warning/10 text-warning',
  AMBIGUOUS: 'border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400',
};

const STATUS_ICONS = {
  VERIFIED: CheckCircle2,
  UNVERIFIED: AlertTriangle,
  AMBIGUOUS: CircleHelp,
} as const;

export function FieldPanel({
  extractionId,
  selectedFieldKey,
  onSourceSelect,
}: FieldPanelProps) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState<ExtractedField | null>(null);

  const fieldsQuery = useQuery({
    queryKey: ['extraction', 'fields', extractionId],
    queryFn: ({ signal }) => getExtractionFields(extractionId, signal),
    retry: false,
  });

  if (fieldsQuery.isLoading) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t('fields.loading')}
      </div>
    );
  }

  if (fieldsQuery.isError) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
        <p className="text-sm text-muted-foreground">
          {normalizeError(fieldsQuery.error).detail}
        </p>
        <Button variant="outline" size="sm" onClick={() => void fieldsQuery.refetch()}>
          {t('fields.retry')}
        </Button>
      </div>
    );
  }

  const fields = fieldsQuery.data ?? [];
  if (fields.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center">
        <ScanLine className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
        <p className="text-sm font-medium">{t('fields.emptyTitle')}</p>
        <p className="max-w-sm text-xs text-muted-foreground">{t('fields.emptyHint')}</p>
      </div>
    );
  }

  return (
    <>
      <ScrollArea className="h-full rounded-lg border">
        <ul className="divide-y">
          {fields.map((field) => (
            <FieldRow
              key={field.id}
              field={field}
              selected={selectedFieldKey === field.fieldKey}
              onEdit={() => setEditing(field)}
              onSourceSelect={(source) => onSourceSelect(field, source)}
            />
          ))}
        </ul>
      </ScrollArea>

      {editing && (
        <CorrectionDialog
          field={editing}
          onOpenChange={(open) => {
            if (!open) setEditing(null);
          }}
        />
      )}
    </>
  );
}

interface FieldRowProps {
  field: ExtractedField;
  selected: boolean;
  onEdit: () => void;
  onSourceSelect: (source: FieldSource | null) => void;
}

function FieldRow({ field, selected, onEdit, onSourceSelect }: FieldRowProps) {
  const { t } = useTranslation();
  const StatusIcon = STATUS_ICONS[field.verificationStatus];

  return (
    <li
      className={cn(
        'space-y-3 p-3 transition-colors sm:p-4',
        selected ? 'bg-primary/5' : 'bg-transparent',
      )}
      data-testid={`field-${field.fieldKey}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {field.label}
          </p>
          <p
            dir="auto"
            className="mt-1 break-words font-mono text-sm font-semibold leading-relaxed"
          >
            {field.effectiveValue}
          </p>
          <p className="mt-1 text-[11px] text-muted-foreground">
            {t('fields.rawValue')}: <span dir="auto">{field.rawValue}</span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <Badge
            variant="outline"
            className={cn('gap-1 font-normal', STATUS_STYLES[field.verificationStatus])}
          >
            <StatusIcon className="h-3 w-3" aria-hidden="true" />
            {t(`fields.status.${field.verificationStatus}`)}
          </Badge>
          {field.corrected && (
            <Badge variant="secondary" className="font-normal">
              {t('fields.correctedBadge')}
            </Badge>
          )}
          {field.confidence != null && field.extractionMethod === 'OCR' && (
            <Badge variant="outline" className="font-normal">
              {t('result.confidence')}: {Math.round(field.confidence * 100)}%
            </Badge>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {field.sources.length > 0 ? (
          field.sources.map((source) => (
            <Button
              key={source.id}
              variant="outline"
              size="sm"
              className="h-7 gap-1.5 px-2 text-xs"
              onClick={() => onSourceSelect(source)}
            >
              <ScanLine className="h-3 w-3" aria-hidden="true" />
              {t('fields.pageSource', { page: source.pageNumber })}
            </Button>
          ))
        ) : (
          <span className="inline-flex items-center gap-1.5 text-[11px] text-warning">
            <AlertTriangle className="h-3 w-3" aria-hidden="true" />
            {t('fields.noSource')}
          </span>
        )}
        <Button variant="ghost" size="sm" className="h-7 gap-1.5 px-2 text-xs" onClick={onEdit}>
          <PencilLine className="h-3 w-3" aria-hidden="true" />
          {t('fields.correct')}
        </Button>
      </div>
    </li>
  );
}

interface CorrectionDialogProps {
  field: ExtractedField;
  onOpenChange: (open: boolean) => void;
}

function CorrectionDialog({ field, onOpenChange }: CorrectionDialogProps) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [value, setValue] = useState(field.effectiveValue);
  const [reason, setReason] = useState('');

  const mutation = useMutation({
    mutationFn: (input: FieldCorrectionInput) =>
      correctExtractionField(field.documentId, field.fieldKey, input),
    onSuccess: async (updated) => {
      queryClient.setQueryData<ExtractedField[]>(
        ['extraction', 'fields', field.documentId],
        (previous) =>
          previous?.map((candidate) =>
            candidate.id === updated.id ? updated : candidate,
          ),
      );
      await queryClient.invalidateQueries({
        queryKey: ['extraction', 'fields', field.documentId],
      });
      toast.success(t('fields.corrected'));
      onOpenChange(false);
    },
    onError: (error) => {
      toast.error(normalizeError(error).detail);
    },
  });

  const dateFormatter = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const history = [...field.corrections].reverse();

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('fields.dialogTitle', { label: field.label })}</DialogTitle>
          <DialogDescription>{t('fields.dialogDescription')}</DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            const trimmed = value.trim();
            if (!trimmed) return;
            mutation.mutate({
              value: trimmed,
              reason: reason.trim() || undefined,
            });
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="field-correction-value">{t('fields.value')}</Label>
            <Textarea
              id="field-correction-value"
              value={value}
              onChange={(event) => setValue(event.target.value)}
              dir="auto"
              rows={3}
              spellCheck={false}
              className="font-mono"
              autoFocus
            />
            <p className="text-[11px] text-muted-foreground">
              {t('fields.originalValue')}: <span dir="auto">{field.rawValue}</span>
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="field-correction-reason">{t('fields.reason')}</Label>
            <Textarea
              id="field-correction-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={2}
              maxLength={500}
            />
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              {t('fields.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending || !value.trim()}>
              {mutation.isPending && <Loader2 className="me-2 h-3.5 w-3.5 animate-spin" />}
              {t('fields.save')}
            </Button>
          </DialogFooter>
        </form>

        <section className="space-y-2 border-t pt-3">
          <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            <History className="h-3.5 w-3.5" aria-hidden="true" />
            {t('fields.history')}
          </h3>
          {history.length === 0 ? (
            <p className="text-xs text-muted-foreground">{t('fields.noHistory')}</p>
          ) : (
            <ol className="space-y-2">
              {history.map((correction) => (
                <li
                  key={correction.id}
                  className="rounded-md border bg-muted/30 px-3 py-2 text-xs"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span dir="auto" className="break-words font-mono font-medium">
                      {correction.correctedValue}
                    </span>
                    <Badge
                      variant={correction.active ? 'secondary' : 'outline'}
                      className="font-normal"
                    >
                      {correction.active ? t('fields.active') : t('fields.superseded')}
                    </Badge>
                  </div>
                  <p className="mt-1 text-muted-foreground">
                    {correction.actorDisplayName ?? t('fields.unknownActor')}
                    {' · '}
                    {dateFormatter.format(new Date(correction.createdAt))}
                  </p>
                  {correction.reason && (
                    <p className="mt-1 text-muted-foreground">{correction.reason}</p>
                  )}
                </li>
              ))}
            </ol>
          )}
        </section>
      </DialogContent>
    </Dialog>
  );
}
