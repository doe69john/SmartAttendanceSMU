import { useCallback, useMemo, useState } from 'react';
import { CalendarClock, Camera, Loader2, RefreshCcw, Trash2 } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
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
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { deleteFaceData, deleteFaceImage, FaceDataStatus } from '@/lib/api';
import {
  faceDataImageCount,
  faceDataLatestStatus,
  faceDataUpdatedAt,
  hasStoredFaceData,
} from '@/lib/faceDataStatus';

interface FaceDataOverviewProps {
  studentId: string;
  status: FaceDataStatus | null;
  onRefresh: () => void;
  onDeleted: () => void;
}

const formatDate = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  try {
    const date = new Date(value);
    return new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(date);
  } catch (error) {
    console.warn('Unable to format timestamp', error);
    return value;
  }
};

const summarizeStatus = (status?: string | null) => {
  if (!status) {
    return 'pending';
  }
  return status.replace(/_/g, ' ');
};

export const FaceDataOverview = ({
  studentId,
  status,
  onRefresh,
  onDeleted,
}: FaceDataOverviewProps) => {
  const { toast } = useToast();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const hasData = hasStoredFaceData(status);
  const imageCount = useMemo(() => faceDataImageCount(status), [status]);
  const latestStatus = useMemo(() => faceDataLatestStatus(status), [status]);
  const updatedAt = useMemo(() => faceDataUpdatedAt(status), [status]);

  const openConfirm = () => {
    setConfirmOpen(true);
  };

  const handleDelete = useCallback(async () => {
    if (!studentId) {
      return;
    }
    setBusy(true);
    try {
      await deleteFaceData({ studentId });
      await deleteFaceImage(studentId);
      toast({
        title: 'Face data removed',
        description: 'Your stored captures have been deleted.',
      });
      setConfirmOpen(false);
      onDeleted();
    } catch (error) {
      console.error('Failed to delete face data', error);
      toast({
        title: 'Unable to delete face data',
        description: error instanceof Error ? error.message : 'Please try again.',
        variant: 'destructive',
      });
    } finally {
      setBusy(false);
    }
  }, [studentId, toast, onDeleted]);

  const confirmDescription = useMemo(
    () => 'Removing your stored face data means you must recapture photos to use automatic attendance.',
    [],
  );

  return (
    <div className="min-h-screen w-full bg-background text-foreground">
      <div className="mx-auto flex max-w-4xl flex-col gap-6 px-6 py-10">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="rounded-full bg-primary/10 p-3 text-primary">
              <Camera className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-2xl font-semibold">Face data on file</h1>
              <p className="text-sm text-muted-foreground">
                Manage your stored captures before starting a new enrollment session.
              </p>
            </div>
          </div>
          <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:flex-wrap">
            <Button
              variant="secondary"
              className="w-full sm:w-auto"
              onClick={onRefresh}
              disabled={busy}
            >
              <RefreshCcw className="mr-2 h-4 w-4" />
              Refresh
            </Button>
            <Button
              variant="destructive"
              className="w-full sm:w-auto"
              onClick={() => openConfirm()}
              disabled={!hasData || busy}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete face data
            </Button>
          </div>
        </header>

        <section className="grid gap-4 sm:grid-cols-3">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">Images stored</CardTitle>
            </CardHeader>
            <CardContent className="text-3xl font-semibold">
              {busy ? <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /> : imageCount}
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">Latest status</CardTitle>
            </CardHeader>
            <CardContent className="flex items-center gap-2 text-lg font-medium">
              <Badge variant="secondary" className="bg-success/10 text-success border-success/20">
                {summarizeStatus(latestStatus)}
              </Badge>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">Last updated</CardTitle>
            </CardHeader>
            <CardContent className="flex items-center gap-2 text-lg font-medium">
              <CalendarClock className="h-5 w-5 text-muted-foreground" />
              {formatDate(updatedAt)}
            </CardContent>
          </Card>
        </section>
      </div>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete stored face data?</AlertDialogTitle>
            <AlertDialogDescription>{confirmDescription}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Keep data</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => void handleDelete()}
              disabled={busy}
            >
              {busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default FaceDataOverview;
