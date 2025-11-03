import { AlertCircle, CheckCircle2, Clock, Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';

import type { SectionModelMetadata } from '@/lib/api';

export type SessionStartupPhase = 'scheduling' | 'retraining' | 'syncing' | 'finalizing' | 'error';

const STEPS: { key: Exclude<SessionStartupPhase, 'error'>; label: string }[] = [
  { key: 'scheduling', label: 'Scheduling session' },
  { key: 'retraining', label: 'Retraining recognition model' },
  { key: 'syncing', label: 'Syncing models with companion app' },
  { key: 'finalizing', label: 'Finalizing live session' },
];

interface SessionStartupOverlayProps {
  open: boolean;
  phase: SessionStartupPhase;
  message: string;
  metadata?: SectionModelMetadata | null;
  onDismiss?: () => void;
}

function resolveStepIcon(index: number, current: number) {
  if (index < current) {
    return <CheckCircle2 className="h-4 w-4 text-green-500" aria-hidden />;
  }
  if (index === current) {
    return <Loader2 className="h-4 w-4 animate-spin text-primary" aria-hidden />;
  }
  return <Clock className="h-4 w-4 text-muted-foreground" aria-hidden />;
}

export default function SessionStartupOverlay({
  open,
  phase,
  message,
  metadata,
  onDismiss,
}: SessionStartupOverlayProps) {
  if (!open) {
    return null;
  }

  const isError = phase === 'error';
  const activeIndex = Math.max(
    STEPS.findIndex((step) => step.key === phase),
    0,
  );

  const missingCount = metadata?.missingStudentIds?.length ?? 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
      <div
        role="dialog"
        aria-modal="true"
        aria-live="polite"
        className="w-full max-w-md rounded-lg border bg-card p-6 shadow-2xl"
      >
        <div className="flex justify-center">
          {isError ? (
            <AlertCircle className="h-12 w-12 text-destructive" aria-hidden />
          ) : (
            <div
              className="h-12 w-12 rounded-full border-4 border-primary/30 border-t-primary animate-spin"
              aria-hidden
            />
          )}
        </div>
        <h2 className="mt-4 text-center text-xl font-semibold text-foreground">
          {isError ? 'Unable to start live session' : 'Preparing live session'}
        </h2>
        <p
          className={`mt-2 text-center text-sm ${isError ? 'text-destructive' : 'text-muted-foreground'}`}
        >
          {message}
        </p>

        {isError ? (
          <div className="mt-6 flex justify-center">
            {onDismiss && (
              <Button variant="outline" onClick={onDismiss}>
                Close
              </Button>
            )}
          </div>
        ) : (
          <>
            <div className="mt-6 space-y-3">
              {STEPS.map((step, index) => (
                <div key={step.key} className="flex items-center gap-3 text-sm">
                  {resolveStepIcon(index, activeIndex)}
                  <span
                    className={
                      index <= activeIndex ? 'text-foreground font-medium' : 'text-muted-foreground'
                    }
                  >
                    {step.label}
                  </span>
                </div>
              ))}
            </div>

            {metadata && (
              <div className="mt-6 rounded-lg border border-muted-foreground/10 bg-muted p-4 text-sm">
                <p className="font-medium text-foreground">Retraining summary</p>
                <p className="mt-1 text-muted-foreground">
                  Images included: {typeof metadata.imageCount === 'number' ? metadata.imageCount : 'N/A'}
                </p>
                <p className="text-muted-foreground">Students missing face data: {missingCount}</p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
