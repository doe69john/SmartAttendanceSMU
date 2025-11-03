import { useAuth } from '@/hooks/useAuth';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CalendarDays, Clock, TrendingUp, TrendingDown, CheckCircle, XCircle, AlertCircle, Download, Loader2 } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { fetchAttendance, fetchStudentSections, downloadMyAttendanceReport, type PagedResponse, type SectionSummary, type SessionAttendanceRecord, type ReportDownload } from '@/lib/api';
import LoadingSpinner from '@/components/LoadingSpinner';
import { useEffect, useMemo, useState } from 'react';
import { useToast } from '@/hooks/use-toast';

const Attendance = () => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const [selectedSection, setSelectedSection] = useState<string>('all');

  const [page, setPage] = useState(0);
  const pageSize = 25;
  const [downloadFormat, setDownloadFormat] = useState<'csv' | 'xlsx' | null>(null);

  useEffect(() => {
    setPage(0);
  }, [selectedSection]);

  const { data: attendancePage, isLoading } = useQuery<PagedResponse<SessionAttendanceRecord>>({
    queryKey: ['attendance-records', profile?.id, selectedSection, page],
    queryFn: async () => {
      if (!profile?.id) {
        const empty: PagedResponse<SessionAttendanceRecord> = {
          items: [],
          page: 0,
          size: pageSize,
          totalItems: 0,
          totalPages: 0,
        };
        return empty;
      }
      return fetchAttendance({
        studentId: profile.id,
        sectionId: selectedSection !== 'all' ? selectedSection : undefined,
        page,
        size: pageSize,
      });
    },
    placeholderData: (previousData) => previousData,
    enabled: !!profile?.id && profile?.role === 'student'
  });

  const { data: sections } = useQuery<SectionSummary[]>({
    queryKey: ['student-sections', profile?.id],
    queryFn: async () => {
      if (!profile?.id) return [];
      return fetchStudentSections(profile.id);
    },
    enabled: !!profile?.id && profile?.role === 'student'
  });

  const attendanceRecords = useMemo<SessionAttendanceRecord[]>(
    () => attendancePage?.items ?? [],
    [attendancePage?.items],
  );
  const sectionOptions = useMemo<SectionSummary[]>(() => sections ?? [], [sections]);

  const sectionLookup = useMemo(() => {
    const map = new Map<string, SectionSummary>();
    sectionOptions.forEach(section => {
      if (section.id) {
        map.set(section.id, section);
      }
    });
    return map;
  }, [sectionOptions]);

  const triggerDownload = (download: ReportDownload) => {
    const url = URL.createObjectURL(download.blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = download.filename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  };

  const handleDownload = async (format: 'csv' | 'xlsx') => {
    if (!profile?.id || profile.role !== 'student') {
      return;
    }
    try {
      setDownloadFormat(format);
      const sectionId = selectedSection !== 'all'
        ? selectedSection
        : undefined;
      const download = await downloadMyAttendanceReport({ format, sectionId });
      triggerDownload(download);
      toast({
        title: 'Download started',
        description: sectionId
          ? `Attendance for the selected section exported as ${format.toUpperCase()}.`
          : `Attendance history exported as ${format.toUpperCase()}.`,
      });
    } catch (error) {
      console.error('Failed to download attendance history', error);
      toast({
        title: 'Download failed',
        description: 'Unable to export attendance history. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setDownloadFormat(null);
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'present':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'late':
        return <AlertCircle className="w-4 h-4 text-yellow-500" />;
      case 'absent':
        return <XCircle className="w-4 h-4 text-red-500" />;
      default:
        return <Clock className="w-4 h-4 text-gray-500" />;
    }
  };

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case 'present':
        return 'default';
      case 'late':
        return 'secondary';
      case 'absent':
        return 'destructive';
      default:
        return 'outline';
    }
  };

  const stats = useMemo(() => {
    if (!attendanceRecords) {
      return { total: 0, present: 0, late: 0, absent: 0, percentage: 0 };
    }
    const total = attendanceRecords.length;
    const present = attendanceRecords.filter((record) => record.status === 'present').length;
    const late = attendanceRecords.filter((record) => record.status === 'late').length;
    const absent = attendanceRecords.filter((record) => record.status === 'absent').length;
    const percentage = total > 0 ? Math.round(((present + late) / total) * 100) : 0;
    return { total, present, late, absent, percentage };
  }, [attendanceRecords]);

  if (isLoading) return <LoadingSpinner />;

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold">Attendance History</h1>
          <p className="text-muted-foreground">
            Track your attendance across all courses
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={selectedSection} onValueChange={setSelectedSection}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="Filter by section" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Sections</SelectItem>
              {sectionOptions.map((section) => (
                <SelectItem key={section.id} value={section.id}>
                  {section.courseCode} - {section.sectionCode}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            size="sm"
            className="flex items-center gap-2"
            disabled={downloadFormat !== null || !profile?.id || profile.role !== 'student'}
            onClick={() => handleDownload('csv')}
          >
            {downloadFormat === 'csv' ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Download className="h-4 w-4" />
            )}
            <span className="hidden sm:inline">CSV</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="flex items-center gap-2"
            disabled={downloadFormat !== null || !profile?.id || profile.role !== 'student'}
            onClick={() => handleDownload('xlsx')}
          >
            {downloadFormat === 'xlsx' ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Download className="h-4 w-4" />
            )}
            <span className="hidden sm:inline">XLSX</span>
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Sessions</CardTitle>
            <CalendarDays className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.total}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Present</CardTitle>
            <CheckCircle className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">{stats.present}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Late</CardTitle>
            <AlertCircle className="h-4 w-4 text-yellow-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">{stats.late}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Attendance Rate</CardTitle>
            {stats.percentage >= 75 ? 
              <TrendingUp className="h-4 w-4 text-green-500" /> : 
              <TrendingDown className="h-4 w-4 text-red-500" />
            }
          </CardHeader>
          <CardContent>
            <div className={`text-2xl font-bold ${stats.percentage >= 75 ? 'text-green-600' : 'text-red-600'}`}>
              {stats.percentage}%
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Attendance Records */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Attendance</CardTitle>
          <CardDescription>
            Your attendance record for recent sessions
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {attendanceRecords.map((record) => {
              const fallbackSection = record.sectionId ? sectionLookup.get(record.sectionId) : undefined;
              const courseLabel = record.courseTitle
                ?? fallbackSection?.courseTitle
                ?? record.courseCode
                ?? fallbackSection?.courseCode
                ?? 'Course';
              const sectionLabel = record.sectionCode
                ?? fallbackSection?.sectionCode
                ?? 'Section';

              return (
                <div key={record.id} className="flex items-center justify-between gap-4 p-4 border rounded-lg">
                  <div className="flex items-center gap-4">
                    {getStatusIcon(record.status)}
                    <div>
                      <p className="font-medium capitalize">{record.status ?? 'pending'}</p>
                      <p className="text-sm text-muted-foreground">
                        Recorded {record.markedAt ? new Date(record.markedAt).toLocaleString() : 'not yet recorded'}
                      </p>
                    </div>
                  </div>
                  <div className="text-right text-sm text-muted-foreground">
                    <p className="font-medium text-foreground">{courseLabel}</p>
                    <p className="text-xs uppercase tracking-wide">Section {sectionLabel}</p>
                    {record.notes && <p className="text-xs mt-1">Notes: {record.notes}</p>}
                  </div>
                  <Badge variant={getStatusBadgeVariant(record.status)} className="shrink-0 capitalize">
                    {record.status ?? 'pending'}
                  </Badge>
                </div>
              );
            })}

            {attendanceRecords.length === 0 && (
              <div className="text-center py-8">
                <CalendarDays className="w-12 h-12 text-muted-foreground mx-auto mb-4" />
                <h3 className="text-lg font-semibold mb-2">No Attendance Records</h3>
                <p className="text-muted-foreground">
                  No attendance records found for the selected section.
                </p>
              </div>
            )}
          </div>

          {attendancePage && attendancePage.totalPages > 1 && (
            <div className="flex justify-end gap-2 pt-4">
              <Button variant="outline" size="sm" onClick={() => setPage((prev) => Math.max(prev - 1, 0))} disabled={page === 0}>
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((prev) => (attendancePage && prev < attendancePage.totalPages - 1 ? prev + 1 : prev))}
                disabled={!attendancePage || page >= attendancePage.totalPages - 1}
              >
                Next
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default Attendance;
