import { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Search, Trash2, Download } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import {
  ApiError,
  deleteFaceData as deleteFaceDataRequest,
  deleteFaceImage,
  downloadFaceImage,
  listFaceData,
  listFaceImages,
} from '@/lib/api';

const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof ApiError ? error.message : fallback;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const pickString = (record: Record<string, unknown>, ...keys: string[]): string | undefined => {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'string' || typeof value === 'number') {
      return String(value);
    }
  }
  return undefined;
};

const pickNumber = (record: Record<string, unknown>, ...keys: string[]): number | undefined => {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'number') {
      return value;
    }
  }
  return undefined;
};

interface FaceDataStudent {
  id?: string;
  fullName?: string;
  studentNumber?: string;
  email?: string;
}

interface FaceDataEntry {
  id: string;
  studentId: string;
  qualityScore?: number;
  confidenceScore?: number;
  processingStatus?: string;
  createdAt?: string;
  student?: FaceDataStudent;
}

export const AdminFaceDataManagement = () => {
  const { toast } = useToast();
  const [faceData, setFaceData] = useState<FaceDataEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedStatus, setSelectedStatus] = useState<string>('all');

  const normalizeStudent = useCallback((entry: unknown): FaceDataStudent | undefined => {
    if (!isRecord(entry)) {
      return undefined;
    }
    const id = pickString(entry, 'id', 'studentId', 'student_id');
    const fullName = pickString(entry, 'fullName', 'full_name');
    const studentNumber = pickString(entry, 'studentNumber', 'student_id', 'studentId');
    const email = pickString(entry, 'email', 'email_address', 'emailAddress');
    if (!id && !fullName && !studentNumber && !email) {
      return undefined;
    }
    return {
      id,
      fullName,
      studentNumber,
      email,
    };
  }, []);

  const loadFaceData = useCallback(async () => {
    try {
      setLoading(true);
      const payload = await listFaceData();
      const items = Array.isArray(payload)
        ? payload
        : (payload as { items?: unknown[] })?.items ?? [];

      const formattedData: FaceDataEntry[] = items
        .filter(isRecord)
        .map((entry) => {
          const metadata = isRecord(entry.metadata) ? entry.metadata : undefined;
          const candidateStudent = [
            entry.student,
            entry.profile,
            entry.student_profile,
            metadata?.student,
          ].find(isRecord);
          const student = normalizeStudent(candidateStudent);
          const studentIdValue = pickString(entry, 'studentId', 'student_id') ?? student?.id ?? '';
        return {
          id: pickString(entry, 'id') ?? '',
          studentId: studentIdValue,
          qualityScore: pickNumber(entry, 'qualityScore', 'quality_score'),
          confidenceScore: pickNumber(entry, 'confidenceScore', 'confidence_score'),
          processingStatus: pickString(entry, 'processingStatus', 'processing_status'),
          createdAt: pickString(entry, 'createdAt', 'created_at'),
          student,
        };
      });

      setFaceData(formattedData);
    } catch (error) {
      console.error('Error loading face data:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to load face data'),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [normalizeStudent, toast]);

  useEffect(() => {
    loadFaceData();
  }, [loadFaceData]);

  const handleDeleteFaceData = async (studentId: string, faceDataId: string) => {
    try {
      await deleteFaceDataRequest({ ids: [faceDataId], studentId });

      try {
        const storageFiles = await listFaceImages(studentId);
        if (Array.isArray(storageFiles) && storageFiles.length > 0) {
          await Promise.allSettled(
            storageFiles.map(file => deleteFaceImage(studentId, file.fileName))
          );
        }
      } catch (error) {
        console.warn('Unable to purge face images for student', studentId, error);
      }

      toast({
        title: "Success",
        description: "Face data deleted successfully. Student will need to re-enroll.",
      });

      loadFaceData();
    } catch (error) {
      console.error('Error deleting face data:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to delete face data'),
        variant: "destructive",
      });
    }
  };

  const triggerDownload = (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const downloadFaceData = async (studentId: string) => {
    try {
      const storageFiles = await listFaceImages(studentId);

      if (!storageFiles || storageFiles.length === 0) {
        toast({
          title: "No Data",
          description: "No face images found for this student",
          variant: "destructive",
        });
        return;
      }

      const file = storageFiles[0];
      const blob = await downloadFaceImage(studentId, file.fileName);
      triggerDownload(blob, `face_data_${studentId}_${file.fileName}`);
    } catch (error) {
      console.error('Error downloading face data:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to download face data'),
        variant: "destructive",
      });
    }
  };

  const STATUS_ALIASES: Record<string, string> = {
    trained: 'completed',
  };

  const normalizeStatusValue = (status?: string) => {
    if (!status) {
      return 'unknown';
    }
    const normalized = status.toLowerCase();
    return STATUS_ALIASES[normalized] ?? normalized;
  };

  const STATUS_LABELS: Record<string, string> = {
    pending: 'Pending',
    processing: 'Processing',
    completed: 'Trained',
    failed: 'Failed',
    unknown: 'Unknown',
  };

  const formatStatusLabel = (status?: string) => {
    const normalized = normalizeStatusValue(status);
    return STATUS_LABELS[normalized] ?? `${normalized.charAt(0).toUpperCase()}${normalized.slice(1)}`;
  };

  const filteredData = faceData.filter(item => {
    const normalizedSearch = searchTerm.trim().toLowerCase();
    const student = item.student;
    const nameMatch = student?.fullName?.toLowerCase().includes(normalizedSearch);
    const idMatch = student?.studentNumber?.toLowerCase().includes(normalizedSearch)
      || item.studentId.toLowerCase().includes(normalizedSearch);
    const emailMatch = student?.email?.toLowerCase().includes(normalizedSearch);
    const matchesSearch =
      normalizedSearch.length === 0 || Boolean(nameMatch || idMatch || emailMatch);

    const matchesStatus = selectedStatus === 'all'
      || normalizeStatusValue(item.processingStatus) === selectedStatus;

    return matchesSearch && matchesStatus;
  });

  const getStatusColor = (status?: string) => {
    switch (normalizeStatusValue(status)) {
      case 'completed':
        return 'bg-green-100 text-green-800';
      case 'processing':
        return 'bg-yellow-100 text-yellow-800';
      case 'pending':
        return 'bg-blue-100 text-blue-800';
      case 'failed':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-primary"></div>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Face Data Management</h1>
        <Button onClick={loadFaceData} variant="outline">
          Refresh Data
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-col md:flex-row gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search by name, student ID, or email..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
            <select
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              className="px-3 py-2 border border-input rounded-md bg-background"
            >
              <option value="all">All Statuses</option>
              <option value="pending">Pending</option>
              <option value="processing">Processing</option>
              <option value="completed">Trained</option>
              <option value="failed">Failed</option>
            </select>
          </div>
        </CardContent>
      </Card>

      {/* Face Data Table */}
      <Card>
        <CardHeader>
          <CardTitle>Enrolled Face Data ({filteredData.length})</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Student</TableHead>
                <TableHead>Student ID</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Quality Score</TableHead>
                <TableHead>Confidence</TableHead>
                <TableHead>Enrolled Date</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredData.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-medium">
                    {item.student?.fullName ?? 'Unknown Student'}
                  </TableCell>
                  <TableCell>{item.student?.studentNumber ?? item.studentId}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {item.student?.email ?? 'N/A'}
                  </TableCell>
                  <TableCell>
                    <Badge className={getStatusColor(item.processingStatus)}>
                      {formatStatusLabel(item.processingStatus)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {typeof item.qualityScore === 'number' ?
                      `${Math.round(item.qualityScore * 100)}%` :
                      'N/A'
                    }
                  </TableCell>
                  <TableCell>
                    {typeof item.confidenceScore === 'number' ?
                      `${Math.round(item.confidenceScore * 100)}%` :
                      'N/A'
                    }
                  </TableCell>
                  <TableCell className="text-sm">
                    {item.createdAt ? new Date(item.createdAt).toLocaleDateString() : 'N/A'}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center space-x-2">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => downloadFaceData(item.studentId)}
                      >
                        <Download className="w-4 h-4" />
                      </Button>
                      
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button size="sm" variant="destructive">
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Delete Face Data</AlertDialogTitle>
                            <AlertDialogDescription>
                              This will permanently delete all face data for{' '}
                              <strong>{item.student?.fullName ?? 'this student'}</strong>. They will need to
                              re-enroll their face data to participate in attendance sessions.
                              This action cannot be undone.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction
                              onClick={() => handleDeleteFaceData(item.studentId, item.id)}
                              className="bg-red-600 hover:bg-red-700"
                            >
                              Delete Face Data
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {filteredData.length === 0 && (
            <div className="text-center py-8 text-muted-foreground">
              No face data found matching your criteria.
            </div>
          )}
        </CardContent>
      </Card>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-bold">
              {faceData.filter(item => normalizeStatusValue(item.processingStatus) === 'completed').length}
            </div>
            <p className="text-sm text-muted-foreground">Trained Models</p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-bold">
              {faceData.filter(item => normalizeStatusValue(item.processingStatus) === 'pending').length}
            </div>
            <p className="text-sm text-muted-foreground">Pending Training</p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-bold">
              {faceData.filter(item => normalizeStatusValue(item.processingStatus) === 'failed').length}
            </div>
            <p className="text-sm text-muted-foreground">Failed Enrollments</p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="text-2xl font-bold">
              {faceData.length
                ? Math.round(
                    faceData.reduce((sum, item) => sum + (item.qualityScore ?? 0), 0) /
                    faceData.length * 100
                  )
                : 0}%
            </div>
            <p className="text-sm text-muted-foreground">Avg Quality Score</p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
