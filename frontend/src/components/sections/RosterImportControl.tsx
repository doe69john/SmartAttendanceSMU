import { useRef, useState, type ChangeEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Loader2, Upload } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import { ApiError, importSectionRoster, type RosterImportSummary } from '@/lib/api';
import { cn } from '@/lib/utils';

const MAX_ISSUE_PREVIEW = 4;

interface RosterImportControlProps {
  disabled?: boolean;
  onImport: (result: RosterImportSummary) => void;
  className?: string;
}

export function RosterImportControl({ disabled, onImport, className }: RosterImportControlProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [summary, setSummary] = useState<RosterImportSummary | null>(null);
  const { toast } = useToast();

  const mutation = useMutation({
    mutationFn: (file: File) => importSectionRoster(file),
    onSuccess: (result) => {
      setSummary(result);
      onImport(result);
      const matched = result.matchedCount ?? 0;
      const processed = result.processedCount ?? 0;
      const duplicateCount = result.duplicateCount ?? 0;
      const description = matched
        ? `${matched} ${matched === 1 ? 'student' : 'students'} matched from ${processed} rows.`
        : `No students matched from ${processed} rows.`;
      const duplicateMessage = duplicateCount
        ? ` ${duplicateCount} duplicate ${duplicateCount === 1 ? 'entry was' : 'entries were'} skipped.`
        : '';
      toast({
        title: matched ? 'Roster processed' : 'No matches found',
        description: description + duplicateMessage,
      });
    },
    onError: (error) => {
      toast({
        title: 'Import failed',
        description: formatError(error, 'Unable to process the roster file.'),
        variant: 'destructive',
      });
    },
  });

  const handleButtonClick = () => {
    if (disabled || mutation.isPending) {
      return;
    }
    inputRef.current?.click();
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    mutation.mutate(file);
    event.target.value = '';
  };

  return (
    <div className={cn('rounded-2xl border border-dashed border-border/50 bg-muted/30 p-4', className)}>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-sm font-semibold">Batch roster import</p>
          <p className="text-xs text-muted-foreground">
            Upload a CSV or XLSX file containing student names, emails, or IDs. We&apos;ll match everything we can find.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={handleButtonClick}
          disabled={disabled || mutation.isPending}
          className="justify-center"
        >
          {mutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Upload className="mr-2 h-4 w-4" />}
          {mutation.isPending ? 'Processing…' : 'Import file'}
        </Button>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept=".csv,.xlsx,.xls"
        className="hidden"
        onChange={handleFileChange}
      />
      {summary ? (
        <div className="mt-3 rounded-xl bg-background/70 p-3 text-xs">
          <p className="text-sm font-medium text-foreground">
            Matched {summary.matchedCount ?? 0} of {summary.processedCount ?? 0} rows
          </p>
          {summary.duplicateCount ? (
            <p className="text-muted-foreground">
              {summary.duplicateCount} duplicate {summary.duplicateCount === 1 ? 'entry was' : 'entries were'} skipped.
            </p>
          ) : null}
          {summary.issues?.length ? (
            <div className="mt-2 space-y-1 text-muted-foreground">
              <p className="font-medium">Issues</p>
              <ul className="list-disc space-y-1 pl-4">
                {summary.issues.slice(0, MAX_ISSUE_PREVIEW).map((issue, index) => (
                  <li key={`${issue.rowNumber ?? 'row'}-${issue.value ?? index}`}>
                    {issue.rowNumber && issue.rowNumber > 0 ? `Row ${issue.rowNumber}: ` : ''}
                    {issue.reason}
                    {issue.value ? ` (${issue.value})` : ''}
                  </li>
                ))}
                {summary.issues.length > MAX_ISSUE_PREVIEW ? (
                  <li className="italic">+ {summary.issues.length - MAX_ISSUE_PREVIEW} more</li>
                ) : null}
              </ul>
            </div>
          ) : (
            <p className="mt-2 text-emerald-600 dark:text-emerald-400">Every processed row was matched successfully.</p>
          )}
        </div>
      ) : (
        <p className="mt-3 text-xs text-muted-foreground">
          Up to 1,000 rows per import. Duplicate students will be filtered automatically.
        </p>
      )}
    </div>
  );
}

function formatError(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message ?? fallback;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}
