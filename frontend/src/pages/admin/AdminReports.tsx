import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft,
  Activity,
  BarChart3,
  BookOpen,
  CalendarDays,
  Download,
  FileText,
  Loader2,
  Search,
  Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { formatCampusDate, formatCampusDateTime, formatCampusTime } from '@/lib/datetime';

import {
  fetchAdminSectionReports,
  fetchAdminStudentReports,
  fetchAdminSectionReportDetail,
  fetchAdminStudentReportDetail,
  downloadAdminSectionReport,
  downloadAdminStudentReport,
  type ProfessorSectionReport,
  type ProfessorStudentReport,
  type ReportDownload,
  type SectionReportDetail,
  type StudentReportDetail,
} from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { useLocation } from 'react-router-dom';

const MIN_QUERY_LENGTH = 2;

interface DownloadState {
  kind: 'student' | 'section';
  id: string;
  format: 'csv' | 'xlsx';
}

const formatPercent = (value: number | undefined | null) => {
  if (!value || Number.isNaN(value)) {
    return '0%';
  }
  return `${Math.round(value * 1000) / 10}%`;
};

const formatDateTime = (value?: string | null) => formatCampusDateTime(value, '-');

const formatTime = (value?: string | null) => formatCampusTime(value, '-');

const formatDate = (value?: string | null) => formatCampusDate(value, '-');

const triggerDownload = ({ blob, filename }: ReportDownload) => {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
};

const AdminReports = () => {
  const { toast } = useToast();
  const location = useLocation();
  const appliedStudentIdRef = useRef<string | null>(null);

  const [activeTab, setActiveTab] = useState<'students' | 'sections'>('students');
  const [studentSearch, setStudentSearch] = useState('');
  const deferredStudentQuery = useDeferredValue(studentSearch.trim());
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(null);

  const [sectionSearch, setSectionSearch] = useState('');
  const deferredSectionQuery = useDeferredValue(sectionSearch.trim());
  const [selectedSectionId, setSelectedSectionId] = useState<string | null>(null);

  const [downloadState, setDownloadState] = useState<DownloadState | null>(null);

  useEffect(() => {
    const state = location.state as { studentId?: string | null } | null;
    const nextStudentId = state?.studentId ?? null;
    if (nextStudentId && appliedStudentIdRef.current !== nextStudentId) {
      setActiveTab('students');
      setSelectedSectionId(null);
      setSelectedStudentId(nextStudentId);
      appliedStudentIdRef.current = nextStudentId;
    }
  }, [location.state]);

  const shouldQueryStudents = deferredStudentQuery.length === 0 || deferredStudentQuery.length >= MIN_QUERY_LENGTH;
  const shouldQuerySections = deferredSectionQuery.length === 0 || deferredSectionQuery.length >= MIN_QUERY_LENGTH;

  const { data: studentReports = [], isLoading: loadingStudents } = useQuery<ProfessorStudentReport[]>({
    queryKey: ['admin-reports', 'students', shouldQueryStudents ? deferredStudentQuery : ''],
    enabled: shouldQueryStudents,
    queryFn: async () => {
      const result = await fetchAdminStudentReports({
        query: deferredStudentQuery || undefined,
      });
      return result ?? [];
    },
    staleTime: 120_000,
  });

  const { data: sectionReports = [], isLoading: loadingSections } = useQuery<ProfessorSectionReport[]>({
    queryKey: ['admin-reports', 'sections', shouldQuerySections ? deferredSectionQuery : ''],
    enabled: shouldQuerySections,
    queryFn: async () => {
      const result = await fetchAdminSectionReports({
        query: deferredSectionQuery || undefined,
      });
      return result ?? [];
    },
    staleTime: 120_000,
  });

  const studentDetailQuery = useQuery<StudentReportDetail | null>({
    queryKey: ['report', 'student', selectedStudentId],
    enabled: Boolean(selectedStudentId),
    queryFn: async () => {
      if (!selectedStudentId) return null;
      return fetchAdminStudentReportDetail(selectedStudentId);
    },
    staleTime: 120_000,
  });

  const sectionDetailQuery = useQuery<SectionReportDetail | null>({
    queryKey: ['report', 'section', selectedSectionId],
    enabled: Boolean(selectedSectionId),
    queryFn: async () => {
      if (!selectedSectionId) return null;
      return fetchAdminSectionReportDetail(selectedSectionId);
    },
    staleTime: 120_000,
  });

  const handleTabChange = useCallback(
    (value: string) => {
      const next = value === 'sections' ? 'sections' : 'students';
      setActiveTab(next);
      if (next === 'students') {
        setSelectedSectionId(null);
      } else {
        setSelectedStudentId(null);
      }
    },
    [setActiveTab, setSelectedSectionId, setSelectedStudentId],
  );

  const handleStudentRowClick = useCallback(
    (report: ProfessorStudentReport) => {
      if (!report.student?.id) return;
      setSelectedSectionId(null);
      setSelectedStudentId(report.student.id);
    },
    [setSelectedSectionId, setSelectedStudentId],
  );

  const handleSectionRowClick = useCallback(
    (report: ProfessorSectionReport) => {
      setSelectedStudentId(null);
      setSelectedSectionId(report.sectionId);
    },
    [setSelectedSectionId, setSelectedStudentId],
  );

  const handleDownload = useCallback(
    async (params: { kind: 'student' | 'section'; id: string; format: 'csv' | 'xlsx' }) => {
      try {
        setDownloadState(params);
        const result =
          params.kind === 'student'
            ? await downloadAdminStudentReport(params.id, params.format)
            : await downloadAdminSectionReport(params.id, params.format);
        triggerDownload(result);
        toast({
          title: 'Download started',
          description: `Report exported as ${params.format.toUpperCase()}.`,
        });
      } catch (error) {
        console.error('Failed to download report', error);
        toast({
          title: 'Download failed',
          description: 'We were unable to generate the requested report. Please try again.',
          variant: 'destructive',
        });
      } finally {
        setDownloadState(null);
      }
    },
    [toast],
  );

  const selectedStudent = useMemo(() => {
    if (!selectedStudentId) return null;
    return studentReports.find(report => report.student?.id === selectedStudentId) ?? null;
  }, [selectedStudentId, studentReports]);

  const selectedSection = useMemo(() => {
    if (!selectedSectionId) return null;
    return sectionReports.find(report => report.sectionId === selectedSectionId) ?? null;
  }, [selectedSectionId, sectionReports]);

  const filteredStudents = useMemo(() => {
    if (!shouldQueryStudents) {
      const normalized = deferredStudentQuery.toLowerCase();
      return studentReports.filter(report =>
        [report.student?.fullName, report.student?.studentNumber, report.student?.email]
          .filter(Boolean)
          .some(value => value!.toLowerCase().includes(normalized)),
      );
    }
    return studentReports;
  }, [deferredStudentQuery, shouldQueryStudents, studentReports]);

  const filteredSections = useMemo(() => {
    if (!shouldQuerySections) {
      const normalized = deferredSectionQuery.toLowerCase();
      return sectionReports.filter(report =>
        [report.courseCode, report.courseTitle, report.sectionCode]
          .filter(Boolean)
          .some(value => value!.toLowerCase().includes(normalized)),
      );
    }
    return sectionReports;
  }, [deferredSectionQuery, sectionReports, shouldQuerySections]);

  const showStudentDetail = activeTab === 'students' && selectedStudentId;
  const showSectionDetail = activeTab === 'sections' && selectedSectionId;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">Reports</h1>
        <p className="text-muted-foreground">
          Comprehensive attendance analytics across all sections and students.
        </p>
      </div>

      <Tabs value={activeTab} onValueChange={handleTabChange} className="space-y-6">
        <TabsList className="grid grid-cols-2 sm:max-w-sm">
          <TabsTrigger value="students" className="flex items-center gap-2">
            <Users className="h-4 w-4" /> Students
          </TabsTrigger>
          <TabsTrigger value="sections" className="flex items-center gap-2">
            <BookOpen className="h-4 w-4" /> Sections
          </TabsTrigger>
        </TabsList>

        <TabsContent value="students" className="space-y-5">
          {showStudentDetail && selectedStudentId ? (
            <StudentDetailView
              summary={studentDetailQuery.data?.summary ?? selectedStudent ?? null}
              detail={studentDetailQuery.data}
              isLoading={studentDetailQuery.isLoading}
              onBack={() => setSelectedStudentId(null)}
              onDownload={format => {
                if (selectedStudentId) {
                  void handleDownload({ kind: 'student', id: selectedStudentId, format });
                }
              }}
              isDownloading={format =>
                Boolean(
                  downloadState?.kind === 'student' &&
                    downloadState.id === selectedStudentId &&
                    downloadState.format === format,
                )
              }
            />
          ) : (
            <StudentListView
              search={studentSearch}
              onSearchChange={setStudentSearch}
              loading={loadingStudents}
              reports={filteredStudents}
              onSelect={handleStudentRowClick}
            />
          )}
        </TabsContent>

        <TabsContent value="sections" className="space-y-5">
          {showSectionDetail && selectedSectionId ? (
            <SectionDetailView
              summary={sectionDetailQuery.data?.summary ?? selectedSection ?? null}
              detail={sectionDetailQuery.data}
              isLoading={sectionDetailQuery.isLoading}
              onBack={() => setSelectedSectionId(null)}
              onDownload={format => {
                if (selectedSectionId) {
                  void handleDownload({ kind: 'section', id: selectedSectionId, format });
                }
              }}
              isDownloading={format =>
                Boolean(
                  downloadState?.kind === 'section' &&
                    downloadState.id === selectedSectionId &&
                    downloadState.format === format,
                )
              }
            />
          ) : (
            <SectionListView
              search={sectionSearch}
              onSearchChange={setSectionSearch}
              loading={loadingSections}
              reports={filteredSections}
              onSelect={handleSectionRowClick}
            />
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
};

interface StudentListViewProps {
  search: string;
  onSearchChange: (value: string) => void;
  loading: boolean;
  reports: ProfessorStudentReport[];
  onSelect: (report: ProfessorStudentReport) => void;
}

const StudentListView = ({ search, onSearchChange, loading, reports, onSelect }: StudentListViewProps) => (
  <Card>
    <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <CardTitle className="flex items-center gap-2 text-xl">
          <Users className="h-5 w-5" /> Student roster
        </CardTitle>
        <CardDescription>View attendance performance across all sections.</CardDescription>
      </div>
      <div className="relative w-full sm:w-72">
        <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search by name or student number"
          className="pl-9"
          value={search}
          onChange={event => onSearchChange(event.target.value)}
        />
      </div>
    </CardHeader>
    <CardContent className="px-0">
      <div className="w-full overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[220px]">Student</TableHead>
              <TableHead className="w-32">Sections</TableHead>
              <TableHead className="w-32">Courses</TableHead>
              <TableHead className="w-40">Attendance rate</TableHead>
              <TableHead className="w-40">Recorded sessions</TableHead>
              <TableHead className="w-48">Last activity</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && (
              <TableRow>
                <TableCell colSpan={6} className="py-6 text-center text-sm text-muted-foreground">
                  <Loader2 className="mr-2 inline h-4 w-4 animate-spin" /> Loading students...
                </TableCell>
              </TableRow>
            )}

            {!loading && reports.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-6 text-center text-sm text-muted-foreground">
                  No students match your filters.
                </TableCell>
              </TableRow>
            )}

            {reports.map((report, index) => {
              const key =
                report.student?.id ??
                report.student?.studentNumber ??
                report.student?.email ??
                `student-${index}`;

              return (
                <TableRow
                  key={key}
                  className="cursor-pointer hover:bg-muted/60"
                  onClick={() => onSelect(report)}
                >
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <Avatar className="h-10 w-10">
                        <AvatarImage src={report.student?.avatarUrl ?? undefined} alt={report.student?.fullName ?? 'Student'} />
                        <AvatarFallback>{report.student?.fullName?.slice(0, 2)?.toUpperCase() ?? 'ST'}</AvatarFallback>
                      </Avatar>
                      <div className="space-y-1">
                        <p className="text-sm font-medium leading-none">{report.student?.fullName ?? 'Student'}</p>
                        <p className="text-xs text-muted-foreground">{report.student?.studentNumber ?? '-'}</p>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>{report.sectionCount}</TableCell>
                  <TableCell>{report.courseCount}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{formatPercent(report.attendanceRate)}</Badge>
                  </TableCell>
                  <TableCell>
                    {report.attendedSessions}/{report.recordedSessions}
                  </TableCell>
                  <TableCell>{formatDateTime(report.lastAttendanceAt)}</TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </CardContent>
  </Card>
);

interface SectionListViewProps {
  search: string;
  onSearchChange: (value: string) => void;
  loading: boolean;
  reports: ProfessorSectionReport[];
  onSelect: (report: ProfessorSectionReport) => void;
}

const SectionListView = ({ search, onSearchChange, loading, reports, onSelect }: SectionListViewProps) => (
  <Card>
    <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <CardTitle className="flex items-center gap-2 text-xl">
          <BookOpen className="h-5 w-5" /> Teaching sections
        </CardTitle>
        <CardDescription>Analyse attendance trends for each section you teach.</CardDescription>
      </div>
      <div className="relative w-full sm:w-72">
        <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search by course or section code"
          className="pl-9"
          value={search}
          onChange={event => onSearchChange(event.target.value)}
        />
      </div>
    </CardHeader>
    <CardContent className="px-0">
      <div className="w-full overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[160px]">Course</TableHead>
              <TableHead className="w-32">Section</TableHead>
              <TableHead className="w-32">Enrolled</TableHead>
              <TableHead className="w-36">Total sessions</TableHead>
              <TableHead className="w-36">Completed</TableHead>
              <TableHead className="w-36">Upcoming</TableHead>
              <TableHead className="w-44">Average attendance</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && (
              <TableRow>
                <TableCell colSpan={7} className="py-6 text-center text-sm text-muted-foreground">
                  <Loader2 className="mr-2 inline h-4 w-4 animate-spin" /> Loading sections...
                </TableCell>
              </TableRow>
            )}

            {!loading && reports.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-6 text-center text-sm text-muted-foreground">
                  No sections match your filters.
                </TableCell>
              </TableRow>
            )}

            {reports.map(report => (
              <TableRow
                key={report.sectionId}
                className="cursor-pointer hover:bg-muted/60"
                onClick={() => onSelect(report)}
              >
                <TableCell>
                  <div className="space-y-1">
                    <p className="text-sm font-medium leading-none">{report.courseCode}</p>
                    <p className="text-xs text-muted-foreground">{report.courseTitle}</p>
                  </div>
                </TableCell>
                <TableCell>{report.sectionCode}</TableCell>
                <TableCell>
                  {report.enrolledStudents}
                  {report.maxStudents > 0 && <span className="text-xs text-muted-foreground"> / {report.maxStudents}</span>}
                </TableCell>
                <TableCell>{report.totalSessions}</TableCell>
                <TableCell>{report.completedSessions}</TableCell>
                <TableCell>{report.upcomingSessions}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{formatPercent(report.averageAttendanceRate)}</Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </CardContent>
  </Card>
);

interface StudentDetailViewProps {
  summary: ProfessorStudentReport | null;
  detail: StudentReportDetail | null;
  isLoading: boolean;
  onBack: () => void;
  onDownload: (format: 'csv' | 'xlsx') => void;
  isDownloading: (format: 'csv' | 'xlsx') => boolean;
}

const StudentDetailView = ({ summary, detail, isLoading, onBack, onDownload, isDownloading }: StudentDetailViewProps) => {
  if (isLoading) {
    return (
      <div className="flex flex-col items-center gap-6 py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        <p className="text-sm text-muted-foreground">Loading student report...</p>
      </div>
    );
  }

  const displaySummary = detail?.summary ?? summary;
  const sections = detail?.sections ?? [];
  const history = detail?.attendanceHistory ?? [];

  if (!displaySummary) {
    return (
      <div className="space-y-4">
        <Button onClick={onBack} variant="ghost" size="sm" className="w-fit">
          <ArrowLeft className="mr-2 h-4 w-4" /> Back to students
        </Button>
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            Unable to load the selected student.
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Button onClick={onBack} variant="ghost" size="sm" className="w-fit">
        <ArrowLeft className="mr-2 h-4 w-4" /> Back to students
      </Button>

      <Card>
        <CardHeader className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-3">
            <Avatar className="h-16 w-16">
              <AvatarImage src={displaySummary.student?.avatarUrl ?? undefined} alt={displaySummary.student?.fullName ?? 'Student'} />
              <AvatarFallback>{displaySummary.student?.fullName?.slice(0, 2)?.toUpperCase() ?? 'ST'}</AvatarFallback>
            </Avatar>
            <div className="space-y-1">
              <CardTitle className="text-2xl">{displaySummary.student?.fullName ?? 'Student'}</CardTitle>
              <CardDescription>{displaySummary.student?.email ?? 'No email on file'}</CardDescription>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {(['csv', 'xlsx'] as const).map(format => (
              <Button
                key={format}
                variant="outline"
                size="sm"
                onClick={() => onDownload(format)}
                disabled={isDownloading(format)}
                className="flex items-center gap-2"
              >
                {isDownloading(format) ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                {format.toUpperCase()}
              </Button>
            ))}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <MetricCard icon={BarChart3} label="Attendance rate" value={formatPercent(displaySummary.attendanceRate)} />
            <MetricCard icon={Users} label="Sections" value={displaySummary.sectionCount.toString()} />
            <MetricCard icon={BookOpen} label="Courses" value={displaySummary.courseCount.toString()} />
          </div>
        </CardContent>
      </Card>

      <div className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <h3 className="text-lg font-semibold">Section performance</h3>
          <Badge variant="secondary">{sections.length} section{sections.length === 1 ? '' : 's'}</Badge>
        </div>
        <Card>
          <CardContent className="px-0">
            <div className="w-full overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Course</TableHead>
                    <TableHead>Section</TableHead>
                    <TableHead>Total sessions</TableHead>
                    <TableHead>Recorded</TableHead>
                    <TableHead>Attended</TableHead>
                    <TableHead>Missed</TableHead>
                    <TableHead>Rate</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sections.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} className="py-6 text-center text-sm text-muted-foreground">
                        This student is not enrolled in any active sections.
                      </TableCell>
                    </TableRow>
                  )}
                  {sections.map(section => (
                    <TableRow key={section.sectionId}>
                      <TableCell>{section.courseCode}</TableCell>
                      <TableCell>{section.sectionCode}</TableCell>
                      <TableCell>{section.totalSessions}</TableCell>
                      <TableCell>{section.recordedSessions}</TableCell>
                      <TableCell>{section.attendedSessions}</TableCell>
                      <TableCell>{section.missedSessions}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{formatPercent(section.attendanceRate)}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <h3 className="text-lg font-semibold">Attendance history</h3>
          <Badge variant="secondary" className="flex items-center gap-1">
            <Activity className="h-3.5 w-3.5" /> {history.length} record{history.length === 1 ? '' : 's'}
          </Badge>
        </div>
        <Card>
          <CardContent className="px-0">
            <div className="w-full overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Course</TableHead>
                    <TableHead>Section</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Start</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Marked at</TableHead>
                    <TableHead>Method</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {history.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} className="py-6 text-center text-sm text-muted-foreground">
                        No attendance records have been captured yet.
                      </TableCell>
                    </TableRow>
                  )}
                  {history.map(record => (
                    <TableRow key={`${record.sessionId}-${record.sectionId}`}>
                      <TableCell>{record.courseCode}</TableCell>
                      <TableCell>{record.sectionCode}</TableCell>
                      <TableCell>{formatDate(record.sessionDate)}</TableCell>
                      <TableCell>{formatTime(record.startTime)}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{record.status ? record.status.toUpperCase() : 'PENDING'}</Badge>
                      </TableCell>
                      <TableCell>{formatDateTime(record.markedAt)}</TableCell>
                      <TableCell>{record.markingMethod ? record.markingMethod.toUpperCase() : '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

interface SectionDetailViewProps {
  summary: ProfessorSectionReport | null;
  detail: SectionReportDetail | null;
  isLoading: boolean;
  onBack: () => void;
  onDownload: (format: 'csv' | 'xlsx') => void;
  isDownloading: (format: 'csv' | 'xlsx') => boolean;
}

const SectionDetailView = ({ summary, detail, isLoading, onBack, onDownload, isDownloading }: SectionDetailViewProps) => {
  if (isLoading) {
    return (
      <div className="flex flex-col items-center gap-6 py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        <p className="text-sm text-muted-foreground">Loading section report...</p>
      </div>
    );
  }

  const displaySummary = detail?.summary ?? summary;
  const sessions = detail?.sessions ?? [];

  if (!displaySummary) {
    return (
      <div className="space-y-4">
        <Button onClick={onBack} variant="ghost" size="sm" className="w-fit">
          <ArrowLeft className="mr-2 h-4 w-4" /> Back to sections
        </Button>
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            Unable to load the selected section.
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Button onClick={onBack} variant="ghost" size="sm" className="w-fit">
        <ArrowLeft className="mr-2 h-4 w-4" /> Back to sections
      </Button>

      <Card>
        <CardHeader className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="space-y-1">
            <CardTitle className="text-2xl">
              {displaySummary.courseCode} - {displaySummary.sectionCode}
            </CardTitle>
            <CardDescription>{displaySummary.courseTitle}</CardDescription>
          </div>
          <div className="flex flex-wrap gap-2">
            {(['csv', 'xlsx'] as const).map(format => (
              <Button
                key={format}
                variant="outline"
                size="sm"
                onClick={() => onDownload(format)}
                disabled={isDownloading(format)}
                className="flex items-center gap-2"
              >
                {isDownloading(format) ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                {format.toUpperCase()}
              </Button>
            ))}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              icon={Users}
              label="Enrolled"
              value={`${displaySummary.enrolledStudents}${displaySummary.maxStudents ? ` / ${displaySummary.maxStudents}` : ''}`}
            />
            <MetricCard icon={CalendarDays} label="Total sessions" value={displaySummary.totalSessions.toString()} />
            <MetricCard icon={FileText} label="Completed" value={displaySummary.completedSessions.toString()} />
            <MetricCard icon={BarChart3} label="Avg attendance" value={formatPercent(displaySummary.averageAttendanceRate)} />
          </div>
        </CardContent>
      </Card>

      <div className="space-y-4">
        <h3 className="text-lg font-semibold">Session history</h3>
        <Card>
          <CardContent className="px-0">
            <div className="w-full overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Attendance</TableHead>
                    <TableHead>Notes</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sessions.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} className="py-6 text-center text-sm text-muted-foreground">
                        No sessions have been scheduled yet.
                      </TableCell>
                    </TableRow>
                  )}
                  {sessions.map((session, index) => {
                    const key = session.id ?? `${session.sessionDate ?? 'session'}-${session.startTime ?? 'start'}-${index}`;

                    return (
                      <TableRow key={key}>
                        <TableCell>{formatDate(session.sessionDate as string | null | undefined)}</TableCell>
                        <TableCell>
                          <Badge variant="outline">{session.status ? session.status.toUpperCase() : '-'}</Badge>
                        </TableCell>
                        <TableCell>{session.attendanceSummary ?? '-'}</TableCell>
                        <TableCell>{session.notes ?? '-'}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

interface MetricCardProps {
  icon: LucideIcon;
  label: string;
  value: string;
}

const MetricCard = ({ icon: Icon, label, value }: MetricCardProps) => (
  <Card className="border-muted-foreground/10">
    <CardContent className="flex items-center justify-between gap-4 py-4">
      <div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="text-xl font-semibold">{value}</p>
      </div>
      <div className="rounded-full bg-primary/10 p-2">
        <Icon className="h-5 w-5 text-primary" />
      </div>
    </CardContent>
  </Card>
);

export default AdminReports;
