import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import {
  BookOpen,
  ChevronRight,
  Clock,
  Download,
  Loader2,
  MapPin,
  Pencil,
  PieChart,
  Search,
  Trash2,
  Users,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import { APP_CONFIG } from '@/config/constants';
import {
  ApiError,
  deleteSection,
  downloadSectionReport,
  fetchAdminSections,
  fetchCourses,
  fetchProfessors,
  fetchSectionAnalytics,
  fetchSectionStudents,
  fetchSectionSessions,
  fetchSessionAttendance,
  fetchSessionAttendanceStats,
  searchStudents,
  type AdminSectionSummary,
  type CourseSummary,
  type CreateSectionRequest,
  type ProfessorDirectoryEntry,
  type SectionAnalytics,
  type SectionSummary,
  type SessionAttendanceRecordView,
  type SessionAttendanceStats,
  type SessionSummary,
  type Student,
  type ReportDownload,
  updateAdminSection,
  upsertSectionEnrollment,
} from '@/lib/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format, parseISO } from 'date-fns';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
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
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { useForm, type FieldErrors } from 'react-hook-form';
import { cn } from '@/lib/utils';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';

const MIN_SEARCH_CHARS = 2;

const ISO_WEEKDAYS: Record<number, string> = {
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday',
};

const WEEKDAY_OPTIONS = [
  { value: '1', label: 'Monday' },
  { value: '2', label: 'Tuesday' },
  { value: '3', label: 'Wednesday' },
  { value: '4', label: 'Thursday' },
  { value: '5', label: 'Friday' },
  { value: '6', label: 'Saturday' },
  { value: '7', label: 'Sunday' },
];

const SECTION_CODE_OPTIONS = Array.from({ length: 15 }, (_, index) => `G${index + 1}`);

const statusVariant: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  completed: 'default',
  active: 'secondary',
  scheduled: 'outline',
  cancelled: 'destructive',
};

const SECTION_EMPTY_STATE = (
  <Card className="border-dashed">
    <CardContent className="flex flex-col items-center justify-center gap-3 py-10 text-center">
      <BookOpen className="h-10 w-10 text-muted-foreground" />
      <div className="space-y-1">
        <h3 className="text-lg font-semibold">No sections found</h3>
        <p className="text-sm text-muted-foreground">Adjust the search to locate a section.</p>
      </div>
    </CardContent>
  </Card>
);

const triggerReportDownload = ({ blob, filename }: ReportDownload) => {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
};

const parseTimeToMinutes = (time?: string | null) => {
  if (!time) {
    return Number.NaN;
  }
  const [hours, minutes] = time.split(':').map((segment) => Number.parseInt(segment, 10));
  if (Number.isNaN(hours) || Number.isNaN(minutes)) {
    return Number.NaN;
  }
  return hours * 60 + minutes;
};

const validateTimeOrder = (start?: string | null, end?: string | null): string | null => {
  if (!start || !end) {
    return null;
  }
  const startMinutes = parseTimeToMinutes(start);
  const endMinutes = parseTimeToMinutes(end);
  if (Number.isNaN(startMinutes) || Number.isNaN(endMinutes)) {
    return 'Enter a valid start and end time';
  }
  if (startMinutes >= endMinutes) {
    return 'Start time must be earlier than end time';
  }
  return null;
};

const validateCapacity = (value?: number | null): string | null => {
  if (value == null) {
    return null;
  }
  if (Number.isNaN(value)) {
    return 'Enter a valid capacity';
  }
  if (value <= 0) {
    return 'Capacity must be at least 1 student';
  }
  return null;
};

const formatDate = (value?: string | null) => {
  if (!value) return '—';
  try {
    return format(parseISO(value), 'PP');
  } catch (error) {
    return value;
  }
};

const formatDateTime = (value?: string | null) => {
  if (!value) return '—';
  try {
    return format(parseISO(value), 'PP p');
  } catch (error) {
    return value;
  }
};

const formatMarkingMethod = (method?: string | null) => {
  if (!method) return 'Manual';
  const normalized = method.toLowerCase();
  if (normalized === 'auto' || normalized === 'automatic') {
    return 'Automatic';
  }
  if (normalized === 'manual') {
    return 'Manual';
  }
  return method.charAt(0).toUpperCase() + method.slice(1);
};

const formatTimeRange = (start?: string | null, end?: string | null) => {
  if (!start && !end) {
    return 'Time not set';
  }
  if (!start) {
    return 'Time not set';
  }
  const [startHours, startMinutes] = start.split(':').map((segment) => Number.parseInt(segment, 10));
  const startDate = new Date();
  startDate.setHours(startHours, startMinutes || 0, 0, 0);
  if (!end) {
    return format(startDate, 'h:mm a');
  }
  const [endHours, endMinutes] = end.split(':').map((segment) => Number.parseInt(segment, 10));
  const endDate = new Date();
  endDate.setHours(endHours, endMinutes || 0, 0, 0);
  return `${format(startDate, 'h:mm a')} – ${format(endDate, 'h:mm a')}`;
};

const deriveStatusBadge = (status?: string | null): 'default' | 'secondary' | 'outline' | 'destructive' => {
  if (!status) {
    return 'outline';
  }
  return statusVariant[status.toLowerCase()] ?? 'outline';
};

const normalizeSectionCodeValue = (value?: string | null) => {
  if (!value) {
    return '';
  }
  return value.replace(/\s+/g, '').toUpperCase();
};

const toTimeInput = (value?: string | null) => {
  if (!value) {
    return '';
  }
  if (value.includes(':')) {
    const [hours, minutes] = value.split(':');
    return `${hours.padStart(2, '0')}:${(minutes ?? '00').padStart(2, '0')}`;
  }
  return value;
};

const getErrorMessageFromApi = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    return error.message ?? fallback;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
};

function StudentSelector({
  selected,
  onChange,
  disabled,
  helperText,
}: {
  selected: StudentProfile[];
  onChange: (students: StudentProfile[]) => void;
  disabled?: boolean;
  helperText?: string;
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const deferredQuery = useDeferredValue(searchTerm.trim());
  const shouldSearch = deferredQuery.length === 0 || deferredQuery.length >= MIN_SEARCH_CHARS;

  const { data: directory = [], isFetching } = useQuery<StudentProfile[]>({
    queryKey: ['student-directory', deferredQuery],
    enabled: !disabled && shouldSearch,
    queryFn: async () => {
      const payload = await searchStudents({ query: deferredQuery || undefined, limit: 10 });
      return (payload ?? []) as StudentProfile[];
    },
    staleTime: 120_000,
  });

  const selectedIds = useMemo(
    () => new Set(selected.map((student) => student.id).filter((id): id is string => Boolean(id))),
    [selected],
  );

  const searchResults = useMemo(() => {
    const seen = new Set<string>();
    return directory.filter((student) => {
      const key = student.id ?? `${student.fullName ?? 'student'}-${student.email ?? ''}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
  }, [directory]);

  const handleSelect = (student: StudentProfile) => {
    if (!student.id) {
      return;
    }
    if (selectedIds.has(student.id)) {
      onChange(selected.filter((item) => item.id !== student.id));
    } else {
      onChange([...selected, student]);
    }
  };

  return (
    <div className="space-y-3">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          placeholder="Search students by name or email"
          disabled={disabled}
          className="pl-9"
        />
      </div>
      {helperText ? <p className="text-xs text-muted-foreground">{helperText}</p> : null}
      <ScrollArea className="max-h-52 rounded-md border">
        <div className="divide-y divide-border/60">
          {isFetching ? (
            <div className="flex items-center justify-center gap-2 py-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Searching students…
            </div>
          ) : searchResults.length === 0 ? (
            <div className="py-6 text-center text-sm text-muted-foreground">
              {searchTerm ? 'No students match this search yet.' : 'Start typing to search students.'}
            </div>
          ) : (
            searchResults.map((student) => {
              const isSelected = student.id ? selectedIds.has(student.id) : false;
              return (
                <button
                  key={student.id ?? `${student.fullName}-${student.email}`}
                  type="button"
                  onClick={() => handleSelect(student)}
                  className={cn(
                    'flex w-full items-center justify-between px-4 py-3 text-left text-sm transition',
                    isSelected ? 'bg-primary/10 text-primary' : 'hover:bg-muted/60',
                  )}
                >
                  <div className="flex flex-col">
                    <span className="font-medium">{student.fullName}</span>
                    <span className="text-xs text-muted-foreground">{student.email ?? 'Email unavailable'}</span>
                  </div>
                  {isSelected ? <span className="text-xs font-semibold uppercase">Selected</span> : null}
                </button>
              );
            })
          )}
        </div>
      </ScrollArea>
    </div>
  );
}

export type Section = SectionSummary & {
  enrolledCount: number;
  maxStudents: number;
  dayLabel?: string | null;
  timeRangeLabel?: string | null;
  enrollmentSummary?: string | null;
  professorId?: string | null;
  professorName?: string | null;
  professorEmail?: string | null;
};

type Session = SessionSummary & {
  totalStudents: number;
  attendanceCount: number;
  attendanceRate?: number | null;
  attendanceSummary?: string | null;
  timeRangeLabel?: string | null;
  dayLabel?: string | null;
  presentCount?: number | null;
  lateCount?: number | null;
  presentRate?: number | null;
  lateRate?: number | null;
  recordedStudents?: number | null;
};

type StudentProfile = Student & { email?: string | null };

interface SectionFormValues {
  courseId: string;
  sectionCode: string;
  professorId?: string | null;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  location?: string;
  maxStudents?: number;
  lateThresholdMinutes: number;
}

interface SectionUpdatePayload {
  sectionId: string;
  request: CreateSectionRequest;
  addIds: string[];
  removeIds: string[];
}

const AdminSections = () => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const handleSectionFormInvalid = useCallback(
    (errors: FieldErrors<SectionFormValues>) => {
      const message =
        errors.startTime?.message ||
        errors.endTime?.message ||
        errors.maxStudents?.message ||
        errors.courseId?.message ||
        errors.sectionCode?.message ||
        errors.professorId?.message ||
        errors.dayOfWeek?.message ||
        'Please review the section details before saving.';
      toast({
        title: 'Check section details',
        description: message,
        variant: 'destructive',
      });
    },
    [toast],
  );

  const [sectionSearch, setSectionSearch] = useState('');
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [sectionBeingEdited, setSectionBeingEdited] = useState<Section | null>(null);
  const [editSelectedStudents, setEditSelectedStudents] = useState<StudentProfile[]>([]);
  const [editOriginalRoster, setEditOriginalRoster] = useState<StudentProfile[]>([]);
  const [editRosterHydrated, setEditRosterHydrated] = useState(false);
  const [activeSection, setActiveSection] = useState<Section | null>(null);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [sectionDownloadFormat, setSectionDownloadFormat] = useState<'csv' | 'xlsx' | null>(null);
  const [sessionDownloadState, setSessionDownloadState] = useState<{
    sessionId: string;
    format: 'csv' | 'xlsx';
  } | null>(null);
  const [professorPopoverOpen, setProfessorPopoverOpen] = useState(false);

  const normalizedSearch = sectionSearch.trim();

  const handleApiError = useCallback(
    (error: unknown, fallback: string) => {
      toast({
        title: 'Request failed',
        description: getErrorMessageFromApi(error, fallback),
        variant: 'destructive',
      });
    },
    [toast],
  );

  const { data: sections = [], isLoading: sectionsLoading, isFetching: sectionsFetching } = useQuery<Section[]>({
    queryKey: ['admin-sections', normalizedSearch],
    enabled: profile?.role === 'admin',
    staleTime: 30_000,
    queryFn: async () => {
      const payload = await fetchAdminSections(normalizedSearch ? { query: normalizedSearch } : {});
      return (payload ?? []).map((section: AdminSectionSummary) => {
        const identifier = section.sectionId ?? section.sectionCode ?? '';
        const enrolledCount = section.enrolledCount ?? 0;
        const maxStudents = section.maxStudents ?? 0;
        const dayOfWeek = section.dayOfWeek ?? 1;
        const timeLabel = section.timeRangeLabel ?? formatTimeRange(section.startTime, section.endTime);
        return {
          ...section,
          id: identifier,
          courseId: section.courseId ?? null,
          enrolledCount,
          maxStudents,
          sectionCode: section.sectionCode ?? '',
          dayOfWeek,
          dayLabel: section.dayLabel ?? ISO_WEEKDAYS[dayOfWeek] ?? 'Unscheduled',
          timeRangeLabel: timeLabel,
          enrollmentSummary:
            section.enrollmentSummary ??
            (maxStudents > 0 ? `${enrolledCount}/${maxStudents} seats` : `${enrolledCount} students`),
        } as Section;
      });
    },
  });

  const sectionCodesByCourse = useMemo(() => {
    const map = new Map<string, Set<string>>();
    sections.forEach((section) => {
      if (!section.courseId || !section.sectionCode) {
        return;
      }
      const code = normalizeSectionCodeValue(section.sectionCode);
      if (!code) {
        return;
      }
      if (!map.has(section.courseId)) {
        map.set(section.courseId, new Set<string>());
      }
      map.get(section.courseId)!.add(code);
    });
    return map;
  }, [sections]);

  const { data: courses = [], isLoading: coursesLoading } = useQuery<CourseSummary[]>({
    queryKey: ['courses'],
    enabled: profile?.role === 'admin',
    queryFn: async () => {
      const payload = await fetchCourses();
      return payload ?? [];
    },
  });

  const courseOptions = useMemo(() => {
    return courses
      .filter((course): course is CourseSummary & { id: string } => Boolean(course.id))
      .map((course) => ({
        id: course.id,
        label: `${course.courseCode} — ${course.courseTitle}`,
      }));
  }, [courses]);

  const { data: professors = [] } = useQuery<ProfessorDirectoryEntry[]>({
    queryKey: ['admin-professors'],
    enabled: profile?.role === 'admin',
    queryFn: async () => {
      const payload = await fetchProfessors({ activeOnly: true });
      return payload ?? [];
    },
    staleTime: 60_000,
  });

  const { data: sectionSessions = [], isLoading: sessionsLoading } = useQuery<Session[]>({
    queryKey: ['section-sessions', activeSection?.id],
    enabled: !!activeSection?.id,
    queryFn: async () => {
      if (!activeSection?.id) return [];
      const payload = await fetchSectionSessions(activeSection.id);
      return (payload ?? []).map((session) => {
        const recordedStudents = session.recordedStudents ?? session.attendanceCount ?? 0;
        const attendanceCount = session.attendanceCount ?? recordedStudents ?? 0;
        const totalStudents = session.totalStudents ?? recordedStudents ?? activeSection.enrolledCount ?? 0;
        const presentCount = session.presentCount ?? 0;
        const lateCount = session.lateCount ?? 0;
        const presentRate = session.presentRate ?? (totalStudents > 0 ? presentCount / totalStudents : 0);
        const lateRate = session.lateRate ?? (totalStudents > 0 ? lateCount / totalStudents : 0);
        const attendanceRate = presentRate;
        const attendanceSummary =
          session.attendanceSummary ??
          (totalStudents > 0
            ? `Present ${presentCount}/${totalStudents} • Late ${lateCount}/${totalStudents}`
            : `Present ${presentCount} • Late ${lateCount}`);
        const dayLabel = session.dayLabel ?? (session.sessionDate ? ISO_WEEKDAYS[new Date(session.sessionDate).getDay()] : undefined);
        return {
          ...session,
          totalStudents,
          attendanceCount,
          recordedStudents,
          attendanceRate,
          presentCount,
          lateCount,
          presentRate,
          lateRate,
          attendanceSummary,
          dayLabel,
        };
      });
    },
  });

  const orderedSessions = useMemo(() => {
    if (!sectionSessions.length) {
      return [] as Session[];
    }
    const toTimestamp = (session: Session): number => {
      if (!session) return 0;
      const primary = session.startTime ?? session.sessionDate ?? '';
      const parsedPrimary = primary ? Date.parse(primary) : Number.NaN;
      if (!Number.isNaN(parsedPrimary)) {
        return parsedPrimary;
      }
      const fallback = session.sessionDate ? Date.parse(`${session.sessionDate}T00:00:00Z`) : Number.NaN;
      return Number.isNaN(fallback) ? 0 : fallback;
    };
    return [...sectionSessions].sort((a, b) => toTimestamp(b) - toTimestamp(a));
  }, [sectionSessions]);

  const { data: attendanceRecords = [], isLoading: attendanceLoading } = useQuery<SessionAttendanceRecordView[]>({
    queryKey: ['session-attendance', activeSessionId],
    enabled: !!activeSessionId,
    queryFn: async () => {
      if (!activeSessionId) return [];
      const payload = await fetchSessionAttendance(activeSessionId);
      return payload ?? [];
    },
  });

  const editSectionForm = useForm<SectionFormValues>({
    defaultValues: {
      courseId: '',
      sectionCode: '',
      professorId: '',
      dayOfWeek: '',
      startTime: '',
      endTime: '',
      location: '',
      maxStudents: undefined,
      lateThresholdMinutes: APP_CONFIG.lateThresholdMinutes,
    },
  });

  const editCourseId = editSectionForm.watch('courseId');
  const editProfessorId = editSectionForm.watch('professorId');
  const editSectionCodeValue = normalizeSectionCodeValue(editSectionForm.watch('sectionCode'));
  const editStartTime = editSectionForm.watch('startTime');
  const editEndTime = editSectionForm.watch('endTime');
  const sectionBeingEditedCourseId = sectionBeingEdited?.courseId ?? '';
  const sectionBeingEditedCode = normalizeSectionCodeValue(sectionBeingEdited?.sectionCode);

  const editSectionCodeOptions = useMemo(() => {
    const courseKey = editCourseId || sectionBeingEditedCourseId;
    const used = courseKey ? sectionCodesByCourse.get(courseKey) : undefined;
    return SECTION_CODE_OPTIONS.map((code) => ({
      code,
      disabled: Boolean(used?.has(code) && code !== editSectionCodeValue),
    }));
  }, [editCourseId, editSectionCodeValue, sectionBeingEditedCourseId, sectionCodesByCourse]);

  const allEditCodesUsed = useMemo(
    () => editSectionCodeOptions.every((option) => option.disabled),
    [editSectionCodeOptions],
  );

  useEffect(() => {
    if (!editStartTime || !editEndTime) {
      return;
    }
    void editSectionForm.trigger(['startTime', 'endTime']);
  }, [editStartTime, editEndTime, editSectionForm]);

  const { data: sectionAnalytics, isLoading: analyticsLoading } = useQuery<SectionAnalytics>({
    queryKey: ['section-analytics', activeSection?.id],
    enabled: !!activeSection?.id,
    queryFn: async () => {
      if (!activeSection?.id) {
        return {
          totalSessions: 0,
          completedSessions: 0,
          upcomingSessions: 0,
          averageAttendanceRate: 0,
          averagePresentRate: 0,
          averageLateRate: 0,
        };
      }
      const payload = await fetchSectionAnalytics(activeSection.id);
      return {
        ...payload,
        averagePresentRate: payload.averagePresentRate ?? payload.averageAttendanceRate ?? 0,
        averageLateRate: payload.averageLateRate ?? 0,
        averageAttendanceRate: payload.averagePresentRate ?? payload.averageAttendanceRate ?? 0,
      };
    },
  });

  const { data: attendanceStats, isLoading: statsLoading } = useQuery<SessionAttendanceStats>({
    queryKey: ['session-attendance-stats', activeSessionId],
    enabled: !!activeSessionId,
    queryFn: async () => {
      if (!activeSessionId) {
        return {
          total: 0,
          present: 0,
          late: 0,
          absent: 0,
          pending: 0,
          manual: 0,
          automatic: 0,
        };
      }
      const payload = await fetchSessionAttendanceStats(activeSessionId);
      return {
        ...payload,
        total: payload.total ?? 0,
        present: payload.present ?? 0,
        late: payload.late ?? 0,
        absent: payload.absent ?? 0,
        pending: payload.pending ?? 0,
        manual: payload.manual ?? 0,
        automatic: payload.automatic ?? 0,
      };
    },
  });

  const analyticsSnapshot: SectionAnalytics = sectionAnalytics ?? {
    totalSessions: 0,
    completedSessions: 0,
    upcomingSessions: 0,
    averageAttendanceRate: 0,
    averagePresentRate: 0,
    averageLateRate: 0,
  };

  const averagePresentPercent = Math.round((analyticsSnapshot.averagePresentRate ?? 0) * 100);
  const averageLatePercent = Math.round((analyticsSnapshot.averageLateRate ?? 0) * 100);

  const attendanceSnapshot: SessionAttendanceStats = attendanceStats ?? {
    total: 0,
    present: 0,
    late: 0,
    absent: 0,
    pending: 0,
    manual: 0,
    automatic: 0,
  };

  const { data: editRosterData = [], isFetching: editRosterFetching } = useQuery<StudentProfile[]>({
    queryKey: ['section-roster', sectionBeingEdited?.id],
    enabled: editDialogOpen && !!sectionBeingEdited?.id,
    queryFn: async () => {
      if (!sectionBeingEdited?.id) return [];
      const payload = await fetchSectionStudents(sectionBeingEdited.id);
      return payload ?? [];
    },
  });

  const latestAttendanceUpdate = attendanceRecords.length ? attendanceRecords[0] : null;
  const rosterLoading = editDialogOpen && !editRosterHydrated && editRosterFetching;

  const sectionSheetOpen = Boolean(activeSection);

  const activeSession = useMemo(() => {
    return orderedSessions.find((session) => session.id === activeSessionId) ?? null;
  }, [orderedSessions, activeSessionId]);

  useEffect(() => {
    if (!orderedSessions.length) {
      setActiveSessionId(null);
      return;
    }
    setActiveSessionId((previous) => {
      if (previous && orderedSessions.some((session) => session.id === previous)) {
        return previous;
      }
      return orderedSessions[0]?.id ?? null;
    });
  }, [orderedSessions]);

  useEffect(() => {
    if (!editDialogOpen) {
      return;
    }
    if (!editCourseId) {
      return;
    }
    const usedCodes = sectionCodesByCourse.get(editCourseId);
    if (!usedCodes) {
      return;
    }
    if (!editSectionCodeValue) {
      return;
    }
    const originalCourseId = sectionBeingEditedCourseId;
    const originalCode = sectionBeingEditedCode;
    const belongsToCurrentSection =
      editCourseId === originalCourseId && editSectionCodeValue === originalCode;
    if (usedCodes.has(editSectionCodeValue) && !belongsToCurrentSection) {
      editSectionForm.setValue('sectionCode', '', { shouldValidate: true, shouldDirty: true });
    }
  }, [
    editCourseId,
    editSectionCodeValue,
    editDialogOpen,
    editSectionForm,
    sectionBeingEditedCourseId,
    sectionBeingEditedCode,
    sectionCodesByCourse,
  ]);

  useEffect(() => {
    if (!editDialogOpen) {
      return;
    }
    if (sectionBeingEdited) {
      editSectionForm.reset({
        courseId: sectionBeingEdited.courseId ?? '',
        sectionCode: normalizeSectionCodeValue(sectionBeingEdited.sectionCode),
        professorId: sectionBeingEdited.professorId ?? '',
        dayOfWeek: sectionBeingEdited.dayOfWeek != null ? String(sectionBeingEdited.dayOfWeek) : '',
        startTime: toTimeInput(sectionBeingEdited.startTime),
        endTime: toTimeInput(sectionBeingEdited.endTime),
        location: sectionBeingEdited.location ?? '',
        maxStudents: sectionBeingEdited.maxStudents ?? undefined,
        lateThresholdMinutes: sectionBeingEdited.lateThresholdMinutes ?? APP_CONFIG.lateThresholdMinutes,
      });
      setProfessorPopoverOpen(false);
    }
  }, [editDialogOpen, sectionBeingEdited, editSectionForm]);

  useEffect(() => {
    if (!editDialogOpen || !sectionBeingEdited) {
      return;
    }
    if (!editRosterHydrated && !editRosterFetching) {
      setEditOriginalRoster(editRosterData);
      setEditSelectedStudents(editRosterData);
      setEditRosterHydrated(true);
    }
  }, [editDialogOpen, sectionBeingEdited, editRosterData, editRosterHydrated, editRosterFetching]);

  const editSectionFormRef = useRef<HTMLFormElement | null>(null);

  const editSectionMutation = useMutation({
    mutationFn: async ({ sectionId, request, addIds, removeIds }: SectionUpdatePayload) => {
      const updated = await updateAdminSection(sectionId, request);
      const rosterUpdates: Promise<unknown>[] = [];
      if (addIds.length > 0) {
        rosterUpdates.push(upsertSectionEnrollment(sectionId, { studentIds: addIds, activate: true }));
      }
      if (removeIds.length > 0) {
        rosterUpdates.push(upsertSectionEnrollment(sectionId, { studentIds: removeIds, activate: false }));
      }
      if (rosterUpdates.length > 0) {
        await Promise.all(rosterUpdates);
      }
      return updated;
    },
    onSuccess: (updated) => {
      if (updated?.id) {
        queryClient.invalidateQueries({ queryKey: ['section-roster', updated.id] });
        queryClient.invalidateQueries({ queryKey: ['section-analytics', updated.id] });
        queryClient.invalidateQueries({ queryKey: ['section-sessions', updated.id] });
        setActiveSection((prev) => {
          if (!prev || prev.id !== updated.id) {
            return prev;
          }
          const enrolledCount = updated.enrolledCount ?? prev.enrolledCount;
          return {
            ...prev,
            ...updated,
            enrolledCount,
            maxStudents: updated.maxStudents ?? prev.maxStudents,
          } as Section;
        });
      }
      queryClient.invalidateQueries({ queryKey: ['admin-sections'] });
      toast({
        title: 'Section updated',
        description: 'Section details and roster changes have been saved.',
      });
      handleEditDialogChange(false);
    },
    onError: (error) => handleApiError(error, 'Failed to update section'),
  });

  const deleteSectionMutation = useMutation({
    mutationFn: (sectionId: string) => deleteSection(sectionId),
    onSuccess: (_, sectionId) => {
      queryClient.invalidateQueries({ queryKey: ['admin-sections'] });
      queryClient.invalidateQueries({ queryKey: ['section-roster', sectionId] });
      queryClient.invalidateQueries({ queryKey: ['section-analytics', sectionId] });
      queryClient.invalidateQueries({ queryKey: ['section-sessions', sectionId] });
      setActiveSection((prev) => {
        if (prev?.id === sectionId) {
          setActiveSessionId(null);
          return null;
        }
        return prev;
      });
      toast({
        title: 'Section deleted',
        description: 'The section and its attendance history have been removed.',
      });
      handleEditDialogChange(false);
    },
    onError: (error) => handleApiError(error, 'Failed to delete section'),
  });
  const handleSectionReportDownload = useCallback(
    async (format: 'csv' | 'xlsx') => {
      if (!activeSection?.id) {
        return;
      }
      try {
        setSectionDownloadFormat(format);
        const download = await downloadSectionReport(activeSection.id, { format });
        triggerReportDownload(download);
      } catch (error) {
        handleApiError(error, 'Failed to download section report');
      } finally {
        setSectionDownloadFormat(null);
      }
    },
    [activeSection?.id, handleApiError],
  );

  const handleEditDialogChange = useCallback((open: boolean) => {
    setEditDialogOpen(open);
    if (!open) {
      setSectionBeingEdited(null);
      setEditSelectedStudents([]);
      setEditOriginalRoster([]);
      setEditRosterHydrated(false);
      editSectionForm.reset();
    }
  }, [editSectionForm]);

  const openEditSection = useCallback((section: Section) => {
    setSectionBeingEdited(section);
    setEditDialogOpen(true);
    setEditRosterHydrated(false);
  }, []);

  const handleEditSubmit = useCallback((values: SectionFormValues) => {
    if (!sectionBeingEdited?.id) {
      return;
    }
    const studentIds = editSelectedStudents
      .map((student) => student.id)
      .filter((id): id is string => Boolean(id));
    const originalIds = new Set(
      editOriginalRoster.map((student) => student.id).filter((id): id is string => Boolean(id)),
    );
    const selectedSet = new Set(studentIds);
    const addIds = studentIds.filter((id) => !originalIds.has(id));
    const removeIds = editOriginalRoster
      .map((student) => student.id)
      .filter((id): id is string => Boolean(id) && !selectedSet.has(id));
    const request: CreateSectionRequest = {
      courseId: values.courseId,
      sectionCode: normalizeSectionCodeValue(values.sectionCode),
      dayOfWeek: Number(values.dayOfWeek),
      startTime: values.startTime,
      endTime: values.endTime,
      location: values.location?.trim() || undefined,
      maxStudents: values.maxStudents,
      lateThresholdMinutes: values.lateThresholdMinutes ?? APP_CONFIG.lateThresholdMinutes,
      professorId: values.professorId ? (values.professorId.length > 0 ? values.professorId : undefined) : undefined,
    };
    editSectionMutation.mutate({ sectionId: sectionBeingEdited.id, request, addIds, removeIds });
  }, [editSelectedStudents, editOriginalRoster, editSectionMutation, sectionBeingEdited?.id]);

  const handleConfirmDeleteSection = useCallback(() => {
    if (!sectionBeingEdited?.id || deleteSectionMutation.isPending) {
      return;
    }
    deleteSectionMutation.mutate(sectionBeingEdited.id);
  }, [deleteSectionMutation, sectionBeingEdited?.id]);

  const openSectionDetail = useCallback((section: Section) => {
    setActiveSection(section);
  }, []);

  const handleSectionSheetChange = useCallback((open: boolean) => {
    if (!open) {
      setActiveSessionId(null);
      setActiveSection(null);
      setSessionDownloadState(null);
      setSectionDownloadFormat(null);
    }
  }, []);

  const handleSessionDownload = useCallback(
    async (sessionId: string, format: 'csv' | 'xlsx') => {
      try {
        setSessionDownloadState({ sessionId, format });
        const download = await downloadSectionReport(activeSection?.id ?? sessionId, { format, sessionId });
        triggerReportDownload(download);
      } catch (error) {
        handleApiError(error, 'Failed to download session report');
      } finally {
        setSessionDownloadState(null);
      }
    },
    [activeSection?.id, handleApiError],
  );

  const handleSessionSelect = useCallback((sessionId: string | null) => {
    setActiveSessionId(sessionId);
  }, []);

  const handleRosterStudentClick = useCallback((studentId?: string | null) => {
    if (!studentId) {
      return;
    }
    // Admins stay on the page; extend here if deep linking is desired.
  }, []);

  if (profile?.role !== 'admin') {
    return (
      <div className="p-6">
        <Card>
          <CardContent className="flex flex-col items-center justify-center gap-2 py-10">
            <h3 className="text-lg font-semibold">Access restricted</h3>
            <p className="text-sm text-muted-foreground text-center">
              The administrative sections workspace is reserved for administrators.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const isEditSubmitting = editSectionMutation.isPending;
  const isDeletePending = deleteSectionMutation.isPending;

  return (
    <div className="space-y-8 p-6">
      <header className="space-y-4">
        <div className="space-y-1">
          <h1 className="text-3xl font-bold tracking-tight text-gradient">Section Management</h1>
          <p className="text-muted-foreground">
            Search across every course section, review attendance insights, and keep rosters aligned.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-md">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={sectionSearch}
              onChange={(event) => setSectionSearch(event.target.value)}
              placeholder="Search by course, section code, or professor"
              className="pl-9"
            />
          </div>

          {sectionsFetching ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Refreshing sections…
            </div>
          ) : null}
        </div>
      </header>
      <section className="rounded-3xl border border-border/60 bg-card/70 p-6 shadow-lg shadow-primary/5">
        {sectionsLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <Card key={index} className="animate-pulse border-border/40 bg-muted/30">
                <CardHeader className="space-y-2">
                  <div className="h-5 w-1/2 rounded bg-muted" />
                  <div className="h-4 w-3/4 rounded bg-muted/70" />
                </CardHeader>
                <CardContent className="space-y-3 text-sm text-muted-foreground">
                  <div className="h-3 w-3/4 rounded bg-muted/60" />
                  <div className="h-3 w-1/2 rounded bg-muted/60" />
                  <div className="h-3 w-1/3 rounded bg-muted/40" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : sections.length === 0 ? (
          SECTION_EMPTY_STATE
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {sections.map((section) => {
              const enrollmentLabel = section.enrollmentSummary
                ? section.enrollmentSummary
                : section.maxStudents && section.maxStudents > 0
                  ? `${section.enrolledCount}/${section.maxStudents} seats`
                  : `${section.enrolledCount} students`;
              const timeLabel = section.timeRangeLabel ?? formatTimeRange(section.startTime, section.endTime);
              return (
                <Card
                  key={section.id}
                  onClick={() => openSectionDetail(section)}
                  className="group cursor-pointer border-border/40 bg-gradient-to-br from-card/90 via-card to-background/90 transition hover:border-primary hover:shadow-lg hover:shadow-primary/10"
                >
                  <CardHeader className="space-y-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="space-y-1">
                        <CardTitle className="flex items-center gap-2 text-xl">
                          <BookOpen className="h-5 w-5 text-primary" />
                          {section.courseCode} · {section.sectionCode}
                        </CardTitle>
                        <CardDescription className="text-sm text-muted-foreground">
                          {section.courseTitle}
                        </CardDescription>
                        {section.professorName ? (
                          <p className="text-xs text-muted-foreground">
                            Instructor: <span className="font-medium text-foreground">{section.professorName}</span>
                          </p>
                        ) : (
                          <p className="text-xs text-muted-foreground">Instructor not yet assigned</p>
                        )}
                      </div>
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 rounded-full text-muted-foreground transition hover:text-primary"
                        onClick={(event) => {
                          event.stopPropagation();
                          openEditSection(section);
                        }}
                      >
                        <Pencil className="h-4 w-4" />
                        <span className="sr-only">Edit section</span>
                      </Button>
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-4 w-4" />
                        {section.dayLabel} · {timeLabel}
                      </span>
                      {section.location ? (
                        <span className="inline-flex items-center gap-1">
                          <MapPin className="h-4 w-4" />
                          {section.location}
                        </span>
                      ) : null}
                    </div>
                  </CardHeader>
                  <CardContent className="flex items-center justify-between text-sm text-muted-foreground">
                    <div className="flex items-center gap-2">
                      <Users className="h-4 w-4" />
                      {enrollmentLabel}
                    </div>
                    <div className="flex items-center gap-2 text-primary">
                      <span className="font-medium">View analytics</span>
                      <ChevronRight className="h-4 w-4 transition group-hover:translate-x-1" />
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </section>
      <Dialog open={editDialogOpen} onOpenChange={handleEditDialogChange}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-5xl">
          <DialogHeader>
            <DialogTitle>Edit section</DialogTitle>
            <DialogDescription>
              Adjust schedule details, professor assignment, and roster before saving changes.
            </DialogDescription>
          </DialogHeader>

          {sectionBeingEdited ? (
            <Form {...editSectionForm}>
              <form
                ref={editSectionFormRef}
                onSubmit={editSectionForm.handleSubmit(handleEditSubmit, handleSectionFormInvalid)}
                className="space-y-8"
              >
                <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
                  <div className="space-y-6">
                    <FormField
                      control={editSectionForm.control}
                      name="courseId"
                      rules={{ required: 'Course is required' }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Course</FormLabel>
                          <Select
                            onValueChange={field.onChange}
                            value={field.value}
                            disabled={coursesLoading || courseOptions.length === 0}
                          >
                            <FormControl>
                              <SelectTrigger>
                                <SelectValue
                                  placeholder={
                                    coursesLoading
                                      ? 'Loading courses...'
                                      : courseOptions.length === 0
                                        ? 'No courses available'
                                        : 'Select course'
                                  }
                                />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              {courseOptions.length === 0 ? (
                                <SelectItem value="__no-course" disabled>
                                  No courses available
                                </SelectItem>
                              ) : (
                                courseOptions.map((course) => (
                                  <SelectItem key={course.id} value={course.id}>
                                    {course.label}
                                  </SelectItem>
                                ))
                              )}
                            </SelectContent>
                          </Select>
                          <FormMessage />
                        </FormItem>
                      )}
                    />

                    <FormField
                      control={editSectionForm.control}
                      name="professorId"
                      render={({ field }) => {
                        const selectedProfessor = professors.find((professor) => professor.id === field.value);
                        return (
                          <FormItem>
                            <FormLabel>Assigned professor</FormLabel>
                            <Popover open={professorPopoverOpen} onOpenChange={setProfessorPopoverOpen}>
                              <PopoverTrigger asChild>
                                <Button
                                  type="button"
                                  variant="outline"
                                  role="combobox"
                                  className="w-full justify-between"
                                >
                                  {selectedProfessor ? (
                                    <span className="truncate text-left">
                                      {selectedProfessor.fullName}
                                      {selectedProfessor.email ? (
                                        <span className="block text-xs text-muted-foreground">
                                          {selectedProfessor.email}
                                        </span>
                                      ) : null}
                                    </span>
                                  ) : (
                                    'Unassigned'
                                  )}
                                  <Search className="ml-2 h-4 w-4 text-muted-foreground" />
                                </Button>
                              </PopoverTrigger>
                              <PopoverContent className="p-0" align="start">
                                <Command>
                                  <CommandInput placeholder="Search professors..." />
                                  <CommandList>
                                    <CommandEmpty>No professors found.</CommandEmpty>
                                    <CommandGroup>
                                      <CommandItem
                                        value=""
                                        onSelect={() => {
                                          field.onChange('');
                                          setProfessorPopoverOpen(false);
                                        }}
                                      >
                                        <span className="text-sm text-muted-foreground">Unassigned</span>
                                      </CommandItem>
                                      {professors.map((professor) => (
                                        <CommandItem
                                          key={professor.id}
                                          value={professor.fullName ?? professor.email ?? professor.staffId ?? ''}
                                          onSelect={() => {
                                            field.onChange(professor.id ?? '');
                                            setProfessorPopoverOpen(false);
                                          }}
                                        >
                                          <div>
                                            <p className="font-medium">{professor.fullName ?? 'Unnamed professor'}</p>
                                            {professor.email ? (
                                              <p className="text-xs text-muted-foreground">{professor.email}</p>
                                            ) : null}
                                          </div>
                                        </CommandItem>
                                      ))}
                                    </CommandGroup>
                                  </CommandList>
                                </Command>
                              </PopoverContent>
                            </Popover>
                            <FormMessage />
                          </FormItem>
                        );
                      }}
                    />

                    <div className="grid gap-4 md:grid-cols-2">
                      <FormField
                        control={editSectionForm.control}
                        name="sectionCode"
                        rules={{ required: 'Section code is required' }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Section code</FormLabel>
                            <Select
                              onValueChange={field.onChange}
                              value={field.value}
                              disabled={allEditCodesUsed && !field.value}
                            >
                              <FormControl>
                                <SelectTrigger>
                                  <SelectValue
                                    placeholder={
                                      allEditCodesUsed && !field.value
                                        ? 'All section codes in use'
                                        : 'Select section code'
                                    }
                                  />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                {editSectionCodeOptions.map((option) => (
                                  <SelectItem key={option.code} value={option.code} disabled={option.disabled}>
                                    {option.code}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={editSectionForm.control}
                        name="dayOfWeek"
                        rules={{ required: 'Meeting day is required' }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Meeting day</FormLabel>
                            <Select onValueChange={field.onChange} value={field.value}>
                              <FormControl>
                                <SelectTrigger>
                                  <SelectValue placeholder="Select day" />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                {WEEKDAY_OPTIONS.map((option) => (
                                  <SelectItem key={option.value} value={option.value}>
                                    {option.label}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField
                        control={editSectionForm.control}
                        name="startTime"
                        rules={{
                          required: 'Start time is required',
                          validate: () => validateTimeOrder(editStartTime, editEndTime),
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Start time</FormLabel>
                            <FormControl>
                              <Input type="time" value={field.value ?? ''} onChange={field.onChange} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={editSectionForm.control}
                        name="endTime"
                        rules={{
                          required: 'End time is required',
                          validate: () => validateTimeOrder(editStartTime, editEndTime),
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>End time</FormLabel>
                            <FormControl>
                              <Input type="time" value={field.value ?? ''} onChange={field.onChange} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>

                    <FormField
                      control={editSectionForm.control}
                      name="location"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Location</FormLabel>
                          <FormControl>
                            <Input
                              placeholder="Room or link"
                              value={field.value ?? ''}
                              onChange={(event) => field.onChange(event.target.value)}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />

                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField
                        control={editSectionForm.control}
                        name="maxStudents"
                        rules={{ validate: (value) => validateCapacity(value) }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Capacity</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={1}
                                placeholder="Optional"
                                value={field.value ?? ''}
                                onChange={(event) => {
                                  const inputValue = event.target.value;
                                  if (inputValue === '') {
                                    field.onChange(undefined);
                                  } else {
                                    const parsed = Number.parseInt(inputValue, 10);
                                    field.onChange(Number.isNaN(parsed) ? undefined : parsed);
                                  }
                                }}
                                onBlur={(event) => {
                                  const parsed = Number.parseInt(event.target.value, 10);
                                  if (Number.isNaN(parsed)) {
                                    editSectionForm.setValue('maxStudents', undefined);
                                  }
                                  void editSectionForm.trigger('maxStudents');
                                }}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={editSectionForm.control}
                        name="lateThresholdMinutes"
                        rules={{
                          validate: (value) => {
                            if (value == null) {
                              return null;
                            }
                            if (Number.isNaN(value)) {
                              return 'Enter a valid threshold';
                            }
                            if (value < 0) {
                              return 'Threshold cannot be negative';
                            }
                            if (value > 240) {
                              return 'Threshold should be less than 4 hours';
                            }
                            return null;
                          },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Late threshold (minutes)</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={0}
                                max={240}
                                value={field.value ?? APP_CONFIG.lateThresholdMinutes}
                                onChange={(event) => {
                                  const value = Number.parseInt(event.target.value, 10);
                                  field.onChange(Number.isNaN(value) ? APP_CONFIG.lateThresholdMinutes : value);
                                }}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </div>

                  <div className="space-y-4 rounded-2xl border border-border/60 bg-muted/20 p-4 lg:p-6">
                    <div>
                      <h3 className="text-base font-semibold">Manage enrolled students</h3>
                      <p className="text-xs text-muted-foreground">
                        Search to add or remove students from this section. Changes apply once you save.
                      </p>
                    </div>
                    <StudentSelector
                      selected={editSelectedStudents}
                      onChange={setEditSelectedStudents}
                      disabled={rosterLoading || isEditSubmitting}
                      helperText="Students added here will be enrolled immediately when you save."
                    />
                    {rosterLoading ? (
                      <p className="text-xs text-muted-foreground">Loading current roster...</p>
                    ) : null}
                  </div>
                </div>

                <div className="flex flex-col-reverse gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button
                        type="button"
                        variant="outline"
                        className="w-full justify-center border-destructive/40 text-destructive hover:border-destructive hover:text-destructive lg:w-auto lg:justify-start"
                        disabled={isDeletePending}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        Delete section
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Delete this section?</AlertDialogTitle>
                        <AlertDialogDescription>
                          Removing the section will permanently delete all session rosters and attendance history and will unenroll
                          every student. This action cannot be undone.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel disabled={isDeletePending}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                          onClick={handleConfirmDeleteSection}
                          disabled={isDeletePending}
                          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        >
                          {isDeletePending ? (
                            <>
                              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                              Deleting...
                            </>
                          ) : (
                            'Delete section'
                          )}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>

                  <Button type="submit" disabled={isEditSubmitting} className="btn-gradient w-full lg:w-auto">
                    {isEditSubmitting ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Saving changes
                      </>
                    ) : (
                      'Save changes'
                    )}
                  </Button>
                </div>
              </form>
            </Form>
          ) : (
            <div className="py-10 text-center text-sm text-muted-foreground">
              Select a section to edit its details.
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Sheet open={sectionSheetOpen} onOpenChange={handleSectionSheetChange}>
        <SheetContent
          side="top"
          className="flex h-full w-full max-h-screen flex-col overflow-hidden p-0 sm:rounded-t-lg"
        >
          {activeSection ? (
            <>
              <div className="flex flex-wrap items-center justify-end gap-2 border-b border-border/60 p-4 sm:p-6">
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-8 gap-2"
                    disabled={sectionDownloadFormat === 'csv'}
                    onClick={() => handleSectionReportDownload('csv')}
                  >
                    <Download className="h-4 w-4" />
                    CSV
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-8 gap-2"
                    disabled={sectionDownloadFormat === 'xlsx'}
                    onClick={() => handleSectionReportDownload('xlsx')}
                  >
                    <Download className="h-4 w-4" />
                    XLSX
                  </Button>
                </div>
              </div>

              <SheetHeader className="space-y-3 px-4 pt-4 text-left sm:px-6">
                <SheetTitle className="text-2xl font-semibold sm:text-3xl">
                  {activeSection.courseCode} · {activeSection.sectionCode}
                </SheetTitle>
                <SheetDescription className="text-base text-muted-foreground">
                  {activeSection.courseTitle}
                </SheetDescription>
                <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground sm:text-sm">
                  <span className="inline-flex items-center gap-2">
                    <Users className="h-4 w-4" />
                    {activeSection.professorName ? (
                      <>
                        <span className="font-medium text-foreground">{activeSection.professorName}</span>
                        {activeSection.professorEmail ? ` · ${activeSection.professorEmail}` : ''}
                      </>
                    ) : (
                      'Instructor not assigned'
                    )}
                  </span>
                  {activeSection.location ? (
                    <span className="inline-flex items-center gap-2">
                      <MapPin className="h-4 w-4" />
                      {activeSection.location}
                    </span>
                  ) : null}
                </div>
              </SheetHeader>

              <ScrollArea className="flex-1 px-4 pb-6 sm:px-6">
                <div className="space-y-6">
                  <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <Card className="bg-card/80">
                      <CardHeader className="pb-2">
                        <CardDescription>Total sessions</CardDescription>
                        <CardTitle className="text-2xl font-semibold">
                          {analyticsLoading ? (
                            <Loader2 className="h-5 w-5 animate-spin text-primary" />
                          ) : (
                            analyticsSnapshot.totalSessions
                          )}
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="text-sm text-muted-foreground">
                        Completed {analyticsSnapshot.completedSessions} · Upcoming {analyticsSnapshot.upcomingSessions}
                      </CardContent>
                    </Card>

                    <Card className="bg-card/80">
                      <CardHeader className="pb-2">
                        <CardDescription>Average attendance</CardDescription>
                      </CardHeader>
                      <CardContent>
                        {analyticsLoading ? (
                          <div className="flex items-center gap-2 text-muted-foreground">
                            <Loader2 className="h-4 w-4 animate-spin text-primary" />
                            Calculating averages
                          </div>
                        ) : analyticsSnapshot.completedSessions > 0 ? (
                          <div className="space-y-2">
                            <div className="flex items-baseline gap-3">
                              <CardTitle className="text-2xl font-semibold">{`${averagePresentPercent}% present`}</CardTitle>
                              <Badge variant="outline" className="text-xs font-medium">
                                Late {`${averageLatePercent}%`}
                              </Badge>
                            </div>
                            <p className="text-sm text-muted-foreground">
                              Based on completed sessions with captured attendance outcomes.
                            </p>
                          </div>
                        ) : (
                          <div className="text-sm text-muted-foreground">No completed sessions yet.</div>
                        )}
                      </CardContent>
                    </Card>

                    <Card className="bg-card/80">
                      <CardHeader className="pb-2">
                        <CardDescription>Meeting pattern</CardDescription>
                        <CardTitle className="text-lg font-semibold">
                          {activeSection.dayLabel ?? ISO_WEEKDAYS[activeSection.dayOfWeek ?? 1]}
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-1 text-sm text-muted-foreground">
                        <div className="flex items-center gap-2">
                          <Clock className="h-4 w-4" />
                          {activeSection.timeRangeLabel ?? formatTimeRange(activeSection.startTime, activeSection.endTime)}
                        </div>
                        {activeSection.lateThresholdMinutes != null ? (
                          <div className="flex items-center gap-2">
                            <PieChart className="h-4 w-4" />
                            Late after {activeSection.lateThresholdMinutes} minutes
                          </div>
                        ) : null}
                      </CardContent>
                    </Card>

                    <Card className="bg-card/80">
                      <CardHeader className="pb-2">
                        <CardDescription>Enrollment</CardDescription>
                        <CardTitle className="text-2xl font-semibold">
                          {activeSection.enrollmentSummary ?? `${activeSection.enrolledCount} students`}
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="text-sm text-muted-foreground">
                        {activeSection.maxStudents
                          ? `${Math.max(activeSection.maxStudents - activeSection.enrolledCount, 0)} seats available`
                          : 'No capacity limit set'}
                      </CardContent>
                    </Card>
                  </div>

                  <div className="grid gap-5 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                    <Card className="border-border/50 bg-card/70">
                      <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <CardTitle className="text-lg">Session history</CardTitle>
                          <CardDescription>
                            Drill into any session to view attendance outcomes and recognition quality.
                          </CardDescription>
                        </div>
                        <Badge variant="outline" className="flex items-center gap-1 text-xs">
                          <PieChart className="h-3.5 w-3.5" />
                          {analyticsSnapshot.completedSessions} completed
                        </Badge>
                      </CardHeader>
                      <CardContent className="p-0">
                        <ScrollArea className="max-h-[320px] sm:max-h-[420px] xl:max-h-[520px]">
                          <div className="divide-y divide-border/60">
                            {sessionsLoading ? (
                              <div className="flex items-center justify-center gap-2 py-12 text-muted-foreground">
                                <Loader2 className="h-4 w-4 animate-spin" />
                                Loading sessions
                              </div>
                            ) : orderedSessions.length === 0 ? (
                              <div className="py-12 text-center text-sm text-muted-foreground">
                                Sessions scheduled here will appear once attendance is captured.
                              </div>
                            ) : (
                              orderedSessions.map((session) => {
                                const denominator = session.totalStudents || session.recordedStudents || 0;
                                const isSelected = activeSessionId === session.id;
                                return (
                                  <button
                                    key={session.id}
                                    type="button"
                                    onClick={() => handleSessionSelect(session.id ?? null)}
                                    className={cn(
                                      'flex w-full flex-col gap-2 px-5 py-4 text-left transition',
                                      isSelected ? 'bg-primary/5' : 'hover:bg-muted/60',
                                    )}
                                  >
                                    <div className="flex items-center justify-between gap-3">
                                      <div>
                                        <div className="text-sm font-semibold text-foreground">
                                          {formatDate(session.sessionDate)}
                                        </div>
                                        <div className="text-xs text-muted-foreground">
                                          {session.timeRangeLabel ?? formatTimeRange(session.startTime, session.endTime)}
                                        </div>
                                      </div>
                                      <Badge variant={deriveStatusBadge(session.status)} className="capitalize">
                                        {session.status}
                                      </Badge>
                                    </div>
                                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                                      <span>
                                        Present: {session.presentCount ?? 0}
                                        {denominator ? `/${denominator}` : ''}
                                      </span>
                                      <span>
                                        Late: {session.lateCount ?? 0}
                                        {denominator ? `/${denominator}` : ''}
                                      </span>
                                    </div>
                                  </button>
                                );
                              })
                            )}
                          </div>
                        </ScrollArea>
                      </CardContent>
                    </Card>

                    <Card className="border-border/50 bg-card/70">
                      <CardHeader>
                        <CardTitle className="text-lg">
                          {activeSession ? 'Session analytics' : 'Choose a session'}
                        </CardTitle>
                        {activeSession ? (
                          <CardDescription>
                            {formatDate(activeSession.sessionDate)}
                            {activeSession.timeRangeLabel
                              ? ` · ${activeSession.timeRangeLabel}`
                              : ` · ${formatTimeRange(activeSession.startTime, activeSession.endTime)}`}
                          </CardDescription>
                        ) : null}
                      </CardHeader>
                      <CardContent className="space-y-4">
                        {!activeSession ? (
                          <div className="py-12 text-center text-sm text-muted-foreground">
                            Select a session from the list to inspect attendance records.
                          </div>
                        ) : attendanceLoading ? (
                          <div className="flex items-center justify-center gap-2 py-12 text-muted-foreground">
                            <Loader2 className="h-4 w-4 animate-spin" />
                            Loading attendance
                          </div>
                        ) : (
                          <>
                            <div className="grid gap-3 sm:grid-cols-2">
                              <Card className="bg-card/80">
                                <CardHeader className="pb-1">
                                  <CardDescription>Status</CardDescription>
                                  <CardTitle className="text-xl capitalize">{activeSession.status ?? 'pending'}</CardTitle>
                                </CardHeader>
                                <CardContent className="text-xs text-muted-foreground">
                                  {activeSession.notes || 'No session notes provided.'}
                                </CardContent>
                              </Card>

                              <Card className="bg-card/80">
                                <CardHeader className="pb-1">
                                  <CardDescription>Attendance captured</CardDescription>
                                  <CardTitle className="text-xl">
                                    {statsLoading ? (
                                      <Loader2 className="h-4 w-4 animate-spin text-primary" />
                                    ) : (
                                      attendanceSnapshot.total
                                    )}
                                  </CardTitle>
                                </CardHeader>
                                <CardContent className="text-xs text-muted-foreground">
                                  Present {attendanceSnapshot.present} · Late {attendanceSnapshot.late} · Absent
                                  {` ${attendanceSnapshot.absent}`}
                                </CardContent>
                              </Card>

                              <Card className="bg-card/80">
                                <CardHeader className="pb-1">
                                  <CardDescription>Capture method</CardDescription>
                                  <CardTitle className="text-xl">
                                    {statsLoading ? (
                                      <Loader2 className="h-4 w-4 animate-spin text-primary" />
                                    ) : (
                                      <>
                                        {attendanceSnapshot.automatic} auto / {attendanceSnapshot.manual} manual
                                      </>
                                    )}
                                  </CardTitle>
                                </CardHeader>
                                <CardContent className="text-xs text-muted-foreground">
                                  Auto counts are derived from recognition confidence data.
                                </CardContent>
                              </Card>

                              <Card className="bg-card/80">
                                <CardHeader className="pb-1">
                                  <CardDescription>Last update</CardDescription>
                                  <CardTitle className="text-xl">
                                    {latestAttendanceUpdate ? formatDateTime(latestAttendanceUpdate.markedAt) : 'N/A'}
                                  </CardTitle>
                                </CardHeader>
                                <CardContent className="text-xs text-muted-foreground">
                                  {latestAttendanceUpdate
                                    ? 'Most recent attendance entry.'
                                    : 'Attendance has not been marked yet.'}
                                </CardContent>
                              </Card>
                            </div>

                            <div className="rounded-xl border border-border/60">
                              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 px-4 py-3">
                                <div className="flex flex-wrap items-center gap-3">
                                  <div className="flex items-center gap-2 text-sm font-medium">
                                    <Users className="h-4 w-4" />
                                    Attendance roster
                                  </div>
                                  <div className="flex items-center gap-2">
                                    <Button
                                      type="button"
                                      size="sm"
                                      variant="outline"
                                      className="h-8 gap-2"
                                      disabled={
                                        sessionDownloadState?.sessionId === activeSession.id &&
                                        sessionDownloadState.format === 'csv'
                                      }
                                      onClick={() => handleSessionDownload(activeSession.id, 'csv')}
                                    >
                                      <Download className="h-4 w-4" />
                                      CSV
                                    </Button>
                                    <Button
                                      type="button"
                                      size="sm"
                                      variant="outline"
                                      className="h-8 gap-2"
                                      disabled={
                                        sessionDownloadState?.sessionId === activeSession.id &&
                                        sessionDownloadState.format === 'xlsx'
                                      }
                                      onClick={() => handleSessionDownload(activeSession.id, 'xlsx')}
                                    >
                                      <Download className="h-4 w-4" />
                                      XLSX
                                    </Button>
                                  </div>
                                </div>
                                <span className="text-xs text-muted-foreground">{attendanceSnapshot.total} records</span>
                              </div>
                              {attendanceRecords.length === 0 ? (
                                <div className="py-10 text-center text-sm text-muted-foreground">
                                  No attendance captured for this session yet.
                                </div>
                              ) : (
                                <div className="overflow-x-auto">
                                  <Table>
                                    <TableHeader>
                                      <TableRow>
                                        <TableHead>Student</TableHead>
                                        <TableHead>Status</TableHead>
                                        <TableHead>Marked at</TableHead>
                                        <TableHead>Method</TableHead>
                                        <TableHead className="hidden lg:table-cell">Confidence</TableHead>
                                      </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                      {attendanceRecords.map((record) => (
                                        <TableRow
                                          key={record.id}
                                          className={cn(
                                            'transition',
                                            record.student?.id ? 'cursor-pointer hover:bg-muted/50' : 'cursor-default',
                                          )}
                                          onClick={() => handleRosterStudentClick(record.student?.id)}
                                        >
                                          <TableCell>
                                            <div className="flex flex-col">
                                              <span className="font-medium">
                                                {record.student?.fullName ?? 'Unknown student'}
                                              </span>
                                              <span className="text-xs text-muted-foreground">
                                                {record.student?.email ?? 'N/A'}
                                              </span>
                                            </div>
                                          </TableCell>
                                          <TableCell>
                                            <Badge variant={deriveStatusBadge(record.status)} className="capitalize">
                                              {record.status}
                                            </Badge>
                                          </TableCell>
                                          <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                                            {formatDateTime(record.markedAt)}
                                          </TableCell>
                                          <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                                            {formatMarkingMethod(record.markingMethod)}
                                          </TableCell>
                                          <TableCell className="hidden text-sm text-muted-foreground lg:table-cell">
                                            {record.confidenceScore != null
                                              ? `${Math.round(record.confidenceScore * 100) / 100}`
                                              : 'N/A'}
                                          </TableCell>
                                        </TableRow>
                                      ))}
                                    </TableBody>
                                  </Table>
                                </div>
                              )}
                            </div>
                          </>
                        )}
                      </CardContent>
                    </Card>
                  </div>
                </div>
              </ScrollArea>
            </>
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  );
};

export default AdminSections;
