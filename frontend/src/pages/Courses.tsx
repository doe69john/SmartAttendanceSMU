import { useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Calendar, Clock, MapPin, Users, BookOpen, Loader2, Download } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { fetchProfessorSections, fetchStudentSections, fetchStudentSectionDetail, downloadMyAttendanceReport, type StudentSectionDetail, type SectionSummary, type ReportDownload } from '@/lib/api';
import LoadingSpinner from '@/components/LoadingSpinner';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Separator } from '@/components/ui/separator';
import { formatCampusDate, formatCampusDateWithWeekday, formatCampusTime } from '@/lib/datetime';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useToast } from '@/hooks/use-toast';

const Courses = () => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedSection, setSelectedSection] = useState<SectionSummary | null>(null);
  const selectedSectionId = selectedSection?.id ?? null;
  const [detailDownloadFormat, setDetailDownloadFormat] = useState<'csv' | 'xlsx' | null>(null);

  const { data: sections = [], isLoading } = useQuery<SectionSummary[]>({
    queryKey: ['courses-sections', profile?.id, profile?.role],
    queryFn: async () => {
      if (!profile?.id) return [];
      if (profile.role === 'student') {
        return fetchStudentSections(profile.id) as Promise<SectionSummary[]>;
      }
      if (profile.role === 'professor') {
        return fetchProfessorSections(profile.id) as Promise<SectionSummary[]>;
      }
      return [];
    },
    enabled: !!profile?.id && (profile?.role === 'student' || profile?.role === 'professor')
  });

  const { data: detail, isLoading: detailLoading } = useQuery<StudentSectionDetail>({
    queryKey: ['student-section-detail', profile?.id, selectedSectionId],
    enabled: profile?.role === 'student' && detailOpen && !!profile?.id && !!selectedSectionId,
    queryFn: async () => {
      if (!profile?.id || !selectedSectionId) {
        throw new Error('Missing identifiers for section detail');
      }
      return fetchStudentSectionDetail(profile.id, selectedSectionId);
    },
  });

  const getDayName = (dayOfWeek: number) => {
    const labels: Record<number, string> = {
      1: 'Monday',
      2: 'Tuesday',
      3: 'Wednesday',
      4: 'Thursday',
      5: 'Friday',
      6: 'Saturday',
      7: 'Sunday',
    };
    return labels[dayOfWeek] ?? 'Unknown';
  };

  if (isLoading) return <LoadingSpinner />;

  const visibleSections = Array.isArray(sections) ? sections : [];

  const formatTimeRange = (start?: string | null, end?: string | null) => {
    const format = (time?: string | null) => {
      if (!time) return '';
      if (time.length >= 5) {
        return time.slice(0, 5);
      }
      return time;
    };
    const startFormatted = format(start);
    const endFormatted = format(end);
    if (startFormatted && endFormatted) {
      return `${startFormatted} - ${endFormatted}`;
    }
    return startFormatted || endFormatted || 'TBD';
  };

  const formatSessionDate = (value?: string | null) => formatCampusDateWithWeekday(value, '-');

  const formatTimestamp = (value?: string | null) => {
    if (!value) {
      return '-';
    }
    const datePart = formatCampusDate(value, '');
    const timePart = formatCampusTime(value, '');
    if (!datePart && !timePart) {
      console.warn('Unable to format timestamp');
      return '-';
    }
    if (!timePart) {
      return datePart || '-';
    }
    if (!datePart) {
      return timePart;
    }
    return `${datePart} - ${timePart}`;
  };

  const formatStatus = (value?: string | null) => {
    if (!value) {
      return 'Pending';
    }
    const normalized = value.replace(/_/g, ' ').toLowerCase();
    return normalized.replace(/(^|\s)\w/g, (match) => match.toUpperCase());
  };

  const formatMarkingMethod = (value?: string | null) => {
    if (!value) {
      return '-';
    }
    const normalized = value.replace(/_/g, ' ').toLowerCase();
    return normalized.replace(/(^|\s)\w/g, (match) => match.toUpperCase());
  };

  const attendanceHistory = detail?.attendanceHistory ?? [];
  const activeSectionDetail = detail?.section ?? selectedSection ?? null;
  const activeSectionId = activeSectionDetail?.id ?? selectedSection?.id ?? null;

  const handleViewDetails = (section: SectionSummary) => {
    if (profile?.role !== 'student') {
      return;
    }
    setSelectedSection(section);
    setDetailOpen(true);
  };

  const handleDetailOpenChange = (open: boolean) => {
    setDetailOpen(open);
    if (!open) {
      setSelectedSection(null);
    }
  };

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

  const handleSectionDownload = async (format: 'csv' | 'xlsx') => {
    if (!profile?.id || profile.role !== 'student' || !activeSectionId) {
      return;
    }
    try {
      setDetailDownloadFormat(format);
      const download = await downloadMyAttendanceReport({ format, sectionId: activeSectionId ?? undefined });
      triggerDownload(download);
      toast({
        title: 'Download started',
        description: `Section attendance exported as ${format.toUpperCase()}.`,
      });
    } catch (error) {
      console.error('Failed to download section attendance', error);
      toast({
        title: 'Download failed',
        description: 'Unable to export section attendance history. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setDetailDownloadFormat(null);
    }
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">
            {profile?.role === 'student' ? 'My Courses' : 'Teaching Schedule'}
          </h1>
          <p className="text-muted-foreground">
            {profile?.role === 'student' 
              ? 'View your enrolled courses and schedules'
              : 'Manage your teaching sections'
            }
          </p>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {visibleSections.map((section) => (
          <Card key={section.id} className="hover:shadow-lg transition-shadow">
            <CardHeader>
              <div className="flex items-start justify-between">
                <div>
                  <CardTitle className="text-lg">
                    {section.courseCode}
                  </CardTitle>
                  <CardDescription className="text-sm font-medium">
                    {section.courseTitle}
                  </CardDescription>
                </div>
                <Badge variant="secondary">
                  Section {section.sectionCode}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">
                {section.courseDescription}
              </p>

              <div className="space-y-2 text-sm">
                <div className="flex items-center gap-2">
                  <Calendar className="w-4 h-4 text-muted-foreground" />
                  <span>{getDayName(section.dayOfWeek)}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock className="w-4 h-4 text-muted-foreground" />
                  <span>{formatTimeRange(section.startTime, section.endTime)}</span>
                </div>
                {section.location && (
                  <div className="flex items-center gap-2">
                    <MapPin className="w-4 h-4 text-muted-foreground" />
                    <span>{section.location}</span>
                  </div>
                )}
                {profile?.role !== 'student' && section.maxStudents && (
                  <div className="flex items-center gap-2">
                    <Users className="w-4 h-4 text-muted-foreground" />
                    <span>Max {section.maxStudents} students</span>
                  </div>
                )}
              </div>

              <div className="flex gap-2 pt-4">
                <Button
                  size="sm"
                  variant="outline"
                  className="flex-1"
                  onClick={() => handleViewDetails(section)}
                  disabled={profile?.role !== 'student'}
                >
                  <BookOpen className="w-4 h-4 mr-2" />
                  View Details
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {visibleSections.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-8">
            <BookOpen className="w-12 h-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-semibold mb-2">
              {profile?.role === 'student' ? 'No Courses Enrolled' : 'No Sections Assigned'}
            </h3>
            <p className="text-muted-foreground text-center">
              {profile?.role === 'student' 
                ? 'You are not enrolled in any courses yet. Contact your administrator for enrollment.'
                : 'You are not assigned to any sections yet. Contact your administrator for assignments.'
              }
            </p>
          </CardContent>
        </Card>
      )}

      <Sheet open={detailOpen} onOpenChange={handleDetailOpenChange}>
        <SheetContent side="right" className="w-full sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>{activeSectionDetail?.courseTitle ?? 'Section details'}</SheetTitle>
            <SheetDescription>
              {[activeSectionDetail?.courseCode, activeSectionDetail?.sectionCode]
                .filter(Boolean)
                .join(' - ') || 'Section overview'}
            </SheetDescription>
          </SheetHeader>

          <ScrollArea className="mt-6 h-[calc(100vh-8rem)] pr-2">
            <div className="space-y-6 pb-6">
              {profile?.role === 'student' && activeSectionId && (
                <div className="flex justify-end gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    className="flex items-center gap-2"
                    disabled={detailDownloadFormat !== null}
                    onClick={() => handleSectionDownload('csv')}
                  >
                    {detailDownloadFormat === 'csv' ? (
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
                    disabled={detailDownloadFormat !== null}
                    onClick={() => handleSectionDownload('xlsx')}
                  >
                    {detailDownloadFormat === 'xlsx' ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Download className="h-4 w-4" />
                    )}
                    <span className="hidden sm:inline">XLSX</span>
                  </Button>
                </div>
              )}
              {detailLoading ? (
                <div className="flex flex-col items-center gap-4 py-12 text-sm text-muted-foreground">
                  <Loader2 className="h-5 w-5 animate-spin" />
                  Loading section details...
                </div>
              ) : detail ? (
                <>
                  <div className="space-y-3 text-sm">
                    {detail.section?.dayLabel && (
                      <div className="flex items-center gap-2">
                        <Calendar className="h-4 w-4 text-muted-foreground" />
                        <span>{detail.section.dayLabel}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-2">
                      <Clock className="h-4 w-4 text-muted-foreground" />
                      <span>{detail.section?.timeRangeLabel ?? formatTimeRange(detail.section?.startTime, detail.section?.endTime)}</span>
                    </div>
                    {(detail.section?.location ?? selectedSection?.location) && (
                      <div className="flex items-center gap-2">
                        <MapPin className="h-4 w-4 text-muted-foreground" />
                        <span>{detail.section?.location ?? selectedSection?.location}</span>
                      </div>
                    )}
                  </div>

                  <Separator />

                  <div className="space-y-3">
                    <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Attendance history</h3>
                    {attendanceHistory.length === 0 ? (
                      <p className="text-sm text-muted-foreground">No attendance records available for this section yet.</p>
                    ) : (
                      <div className="rounded-lg border">
                        <ScrollArea className="max-h-[60vh] px-1">
                          <Table>
                            <TableHeader>
                              <TableRow>
                                <TableHead>Date</TableHead>
                                <TableHead>Status</TableHead>
                                <TableHead>Marked at</TableHead>
                                <TableHead className="hidden sm:table-cell">Method</TableHead>
                              </TableRow>
                            </TableHeader>
                            <TableBody>
                              {attendanceHistory.map((record) => (
                                <TableRow key={record.sessionId}>
                                  <TableCell>{formatSessionDate(record.sessionDate)}</TableCell>
                                  <TableCell>{formatStatus(record.status)}</TableCell>
                                  <TableCell>{formatTimestamp(record.markedAt)}</TableCell>
                                  <TableCell className="hidden sm:table-cell">{formatMarkingMethod(record.markingMethod)}</TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </ScrollArea>
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">Unable to load section details. Please try again.</p>
              )}
            </div>
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </div>
  );
};

export default Courses;
