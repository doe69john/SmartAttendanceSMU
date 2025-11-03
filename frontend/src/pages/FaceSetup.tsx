import { useCallback, useEffect, useState } from 'react';

import { FaceDataOverview } from '@/components/face/FaceDataOverview';
import { EnhancedFaceCaptureWizard } from '@/components/face/EnhancedFaceCaptureWizard';
import { useAuth } from '@/hooks/useAuth';
import { ApiError, FaceDataStatus, fetchFaceDataStatus } from '@/lib/api';
import { hasStoredFaceData } from '@/lib/faceDataStatus';
import { Button } from '@/components/ui/button';
import { Loader2 } from 'lucide-react';

const FaceSetup = () => {
  const { profile } = useAuth();
  const studentId = profile?.userId ?? null;

  const [status, setStatus] = useState<FaceDataStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState<'wizard' | 'overview'>('wizard');
  const [error, setError] = useState<string | null>(null);

  const refreshStatus = useCallback(async () => {
    if (!studentId) {
      setStatus(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const next = await fetchFaceDataStatus(studentId);
      setStatus(next);
      setMode(hasStoredFaceData(next) ? 'overview' : 'wizard');
    } catch (err) {
      console.error('Failed to fetch face data status', err);
      const message = err instanceof ApiError ? err.message : 'Unable to load face data status.';
      setError(message);
      setStatus(null);
      setMode('wizard');
    } finally {
      setLoading(false);
    }
  }, [studentId]);

  useEffect(() => {
    void refreshStatus();
  }, [refreshStatus]);

  const handleDeleted = useCallback(() => {
    void refreshStatus();
  }, [refreshStatus]);

  const handleWizardComplete = useCallback(() => {
    void refreshStatus();
  }, [refreshStatus]);

  if (!studentId) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-background text-foreground">
        <div className="space-y-4 text-center">
          <h2 className="text-xl font-semibold">Sign in to manage face data</h2>
          <p className="text-sm text-muted-foreground">
            You must be signed in as a student to capture or review face data.
          </p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-background text-foreground">
        <Loader2 className="mb-4 h-8 w-8 animate-spin text-muted-foreground" />
        <p className="text-sm text-muted-foreground">Checking your face enrollment status...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-background text-foreground">
        <div className="space-y-4 text-center">
          <h2 className="text-xl font-semibold">We couldn&apos;t load your face data</h2>
          <p className="text-sm text-muted-foreground">{error}</p>
          <Button onClick={() => void refreshStatus()}>
            Try again
          </Button>
        </div>
      </div>
    );
  }

  if (mode === 'overview' && hasStoredFaceData(status)) {
    return (
      <FaceDataOverview
        studentId={studentId}
        status={status}
        onRefresh={() => void refreshStatus()}
        onDeleted={handleDeleted}
      />
    );
  }

  return (
    <EnhancedFaceCaptureWizard onComplete={handleWizardComplete} />
  );
};

export default FaceSetup;
