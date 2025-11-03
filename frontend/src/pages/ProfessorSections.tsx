import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BookOpen,
  ChevronRight,
  Clock,
  Download,
  Filter,
  Loader2,
  MapPin,
  Pencil,
  PieChart,
  Plus,
  Search,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import { APP_CONFIG } from '@/config/constants';
import {
  ApiError,
  createSection,
  deleteSection,
  fetchCourses,
  fetchProfessorSections,
  fetchSectionAnalytics,
  fetchSectionStudents,
  fetchSectionSessions,
  fetchSessionAttendance,
  fetchSessionAttendanceStats,
  downloadSectionReport,
  searchStudents,
  type CourseSummary,
  type CreateSectionRequest,
  type SectionAnalytics,
  type SessionAttendanceRecordView,
  type SectionSummary,
  type SessionAttendanceStats,
  type SessionSummary,
  type Student,
  type ReportDownload,
  updateSection,
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

type Section = SectionSummary & {
  enrolledCount: number;
  maxStudents: number;
  dayLabel?: string | null;
  timeRangeLabel?: string | null;
  enrollmentSummary?: string | null;
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

type StudentProfile = Student & {
  email?: string | null;
};

interface SectionFormValues {
  courseId: string;
  sectionCode: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  location?: string;
  maxStudents?: number;
   lateThresholdMinutes: number;
}

type SectionUpdatePayload = {
  sectionId: string;
  request: CreateSectionRequest;
  addIds: string[];
  removeIds: string[];
};

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
        <h3 className="text-lg font-semibold">No sections yet</h3>
        <p className="text-sm text-muted-foreground">
          Create your first section to begin scheduling sessions and tracking attendance.
        </p>
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

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError && error.message) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
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

interface StudentSelectorProps {
  selected: StudentProfile[];
  onChange: (students: StudentProfile[]) => void;
  disabled?: boolean;
  helperText?: string;
}

const StudentSelector = ({ selected, onChange, disabled, helperText }: StudentSelectorProps) => {
  const [searchTerm, setSearchTerm] = useState('');
  const deferredQuery = useDeferredValue(searchTerm.trim());
  const shouldSearch = deferredQuery.length === 0 || deferredQuery.length >= MIN_SEARCH_CHARS;

  const { data: directory = [], isFetching } = useQuery<StudentProfile[]>({
    queryKey: ['student-directory', deferredQuery],
    enabled: !disabled && shouldSearch,
    queryFn: async () => {
      const payload = await searchStudents({
        query: deferredQuery || undefined,
        limit: 10,
      });
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

  const handleAddStudent = useCallback(
    (student: StudentProfile) => {
      if (!student.id || selectedIds.has(student.id)) {
        return;
      }
      onChange([...selected, student]);
    },
    [onChange, selected, selectedIds],
  );

  const handleRemoveStudent = useCallback(
    (studentId?: string) => {
      if (!studentId) return;
      onChange(selected.filter((student) => student.id !== studentId));
    },
    [onChange, selected],
  );

  const renderResults = () => {
    if (!shouldSearch) {
      return (
        <div className="py-4 text-center text-sm text-muted-foreground">
          Type at least {MIN_SEARCH_CHARS} characters to search for students.
        </div>
      );
    }

    if (disabled) {
      return (
        <div className="py-4 text-center text-sm text-muted-foreground">Student search is unavailable.</div>
      );
    }

    if (isFetching) {
      return (
        <div className="flex items-center justify-center gap-2 py-6 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Searching directory
        </div>
      );
    }

    if (searchResults.length === 0) {
      return (
        <div className="py-6 text-center text-sm text-muted-foreground">
          {deferredQuery ? 'No students found for this search.' : 'Directory matches will appear here.'}
        </div>
      );
    }

    return (
      <div className="max-h-56 overflow-y-auto pr-1">
        <div className="divide-y divide-border/60">
          {searchResults.map((student, index) => {
            const key = student.id ?? student.email ?? `${student.fullName ?? 'student'}-${index}`;
            const isSelected = Boolean(student.id && selectedIds.has(student.id));
            return (
              <div
                key={key}
                className="flex items-center justify-between gap-4 px-4 py-3 text-left transition hover:bg-muted/60"
              >
                <div className="flex flex-col">
                  <span className="text-sm font-medium text-foreground">{student.fullName ?? 'Unnamed student'}</span>
                  <span className="text-xs text-muted-foreground">
                    {student.email ?? student.studentNumber ?? 'No additional details'}
                  </span>
                </div>
                {student.id ? (
                  <Button
                    type="button"
                    size="sm"
                    variant={isSelected ? 'outline' : 'secondary'}
                    onClick={() => (isSelected ? handleRemoveStudent(student.id) : handleAddStudent(student))}
                    disabled={disabled}
                    className={
                      isSelected ? 'border-destructive/40 text-destructive hover:border-destructive hover:text-destructive' : undefined
                    }
                  >
                    {isSelected ? 'Remove' : 'Add'}
                  </Button>
                ) : null}
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-3">
      <div className="rounded-xl border border-dashed border-border/60 bg-muted/10">
        {selected.length === 0 ? (
          <div className="flex h-24 items-center justify-center px-4 text-sm text-muted-foreground">
            No students selected yet.
          </div>
        ) : (
          <div className="max-h-48 overflow-y-auto pr-1">
            <div className="flex flex-wrap gap-2 p-3">
              {selected.map((student) => (
                <Badge
                  key={student.id ?? student.fullName}
                  variant="secondary"
                  className="flex items-center gap-2 rounded-full bg-background/80 px-3 py-1 text-xs"
                >
                  <span className="font-medium">{student.fullName ?? 'Unnamed student'}</span>
                  <button
                    type="button"
                    onClick={() => handleRemoveStudent(student.id)}
                    className="rounded-full p-0.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
                  >
                    <X className="h-3 w-3" />
                    <span className="sr-only">Remove student</span>
                  </button>
                </Badge>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="space-y-2">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            placeholder="Search students by name, email, or ID"
            className="pl-9"
            disabled={disabled}
          />
        </div>
        {helperText ? <p className="text-xs text-muted-foreground">{helperText}</p> : null}
        <div className="rounded-xl border border-border/60 bg-card/70">{renderResults()}</div>
      </div>
    </div>
  );
};

function formatTime(value?: string | null) {
  if (!value) return 'N/A';
  try {
    const parsed = parseISO(value.length === 5 ? `1970-01-01T${value}:00` : value);
    return format(parsed, 'p');
  } catch (error) {
    return value.slice(0, 5);
  }
}

function formatTimeRange(start?: string | null, end?: string | null) {
  const startLabel = formatTime(start);
  if (!start || startLabel === 'N/A') {
    return startLabel;
  }
  if (!end) {
    return startLabel;
  }
  const endLabel = formatTime(end);
  return endLabel === 'N/A' ? startLabel : `${startLabel} - ${endLabel}`;
}

function toTimeInput(value?: string | null) {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

function formatDate(value?: string | null) {
  if (!value) return 'N/A';
  try {
    return format(parseISO(value), 'PP');
  } catch (error) {
    return value;
  }
}

function formatDateTime(value?: string | null) {
  if (!value) return 'N/A';
  try {
    return format(parseISO(value), 'PP p');
  } catch (error) {
    return value;
  }
}

function deriveStatusBadge(status?: string | null) {
  if (!status) return 'outline';
  return statusVariant[status.toLowerCase()] ?? 'outline';
}

function formatMarkingMethod(method?: string | null) {
  if (!method) return 'Manual';
  if (method.toLowerCase() === 'auto') return 'Automatic';
  if (method.toLowerCase() === 'manual') return 'Manual';
  return method.charAt(0).toUpperCase() + method.slice(1);
}

function normalizeSectionCodeValue(value?: string | null) {
  if (!value) {
    return '';
  }
  return value.replace(/\s+/g, '').toUpperCase();
}

const ProfessorSections = () => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const handleSectionFormInvalid = useCallback(
    (errors: FieldErrors<SectionFormValues>) => {
      const message =
        errors.startTime?.message ||
        errors.endTime?.message ||
        errors.maxStudents?.message ||
        errors.courseId?.message ||
        errors.sectionCode?.message ||
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

  const [searchTerm, setSearchTerm] = useState('');
  const [dayFilter, setDayFilter] = useState<string>('all');
  const [courseFilter, setCourseFilter] = useState<string>('all');
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createSelectedStudents, setCreateSelectedStudents] = useState<StudentProfile[]>([]);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [sectionBeingEdited, setSectionBeingEdited] = useState<Section | null>(null);
  const [editSelectedStudents, setEditSelectedStudents] = useState<StudentProfile[]>([]);
  const [editOriginalRoster, setEditOriginalRoster] = useState<StudentProfile[]>([]);
  const [editRosterHydrated, setEditRosterHydrated] = useState(false);
  const [activeSection, setActiveSection] = useState<Section | null>(null);
  const sectionSheetOpen = Boolean(activeSection);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [sectionDownloadFormat, setSectionDownloadFormat] = useState<'csv' | 'xlsx' | null>(null);
  const [sessionDownloadState, setSessionDownloadState] = useState<{
    sessionId: string;
    format: 'csv' | 'xlsx';
  } | null>(null);

  const normalizedSearch = searchTerm.trim();
  const dayFilterValue = dayFilter !== 'all' ? Number(dayFilter) : undefined;
  const courseFilterValue = courseFilter !== 'all' ? courseFilter : undefined;

  const handleApiError = (error: unknown, fallback: string) => {
    if (error instanceof ApiError) {
      let description = error.message || fallback;
      if (error.status === 409) {
        description = 'Another section already uses that code for the selected course.';
      } else if (error.status === 422) {
        description = 'Section code must be between G1 and G15.';
      } else if (error.status === 400) {
        description = 'Please complete all required section details.';
      }
      toast({
        title: 'Request failed',
        description,
        variant: 'destructive',
      });
      return;
    }
    toast({
      title: 'Request failed',
      description: fallback,
      variant: 'destructive',
    });
  };

  const handleSectionReportDownload = useCallback(
    async (format: 'csv' | 'xlsx') => {
      if (!activeSection) {
        return;
      }
      try {
        setSectionDownloadFormat(format);
        const download = await downloadSectionReport(activeSection.id, { format });
        triggerReportDownload(download);
        toast({
          title: 'Download started',
          description: `Section roster exported as ${format.toUpperCase()}.`,
        });
      } catch (error) {
        console.error('Failed to download section report', error);
        toast({
          title: 'Download failed',
          description: getErrorMessage(error, 'Unable to export section roster.'),
          variant: 'destructive',
        });
      } finally {
        setSectionDownloadFormat(null);
      }
    },
    [activeSection, toast],
  );

  const handleSessionReportDownload = useCallback(
    async (sessionId: string, format: 'csv' | 'xlsx') => {
      if (!activeSection) {
        return;
      }
      try {
        setSessionDownloadState({ sessionId, format });
        const download = await downloadSectionReport(activeSection.id, { format, sessionId });
        triggerReportDownload(download);
        toast({
          title: 'Download started',
          description: `Session roster exported as ${format.toUpperCase()}.`,
        });
      } catch (error) {
        console.error('Failed to download session report', error);
        toast({
          title: 'Download failed',
          description: getErrorMessage(error, 'Unable to export session roster.'),
          variant: 'destructive',
        });
      } finally {
        setSessionDownloadState(null);
      }
    },
    [activeSection, toast],
  );

  const handleRosterStudentClick = useCallback(
    (studentId?: string | null) => {
      if (!studentId) {
        return;
      }
      setActiveSection(null);
      setActiveSessionId(null);
      navigate('/reports', { state: { studentId } });
    },
    [navigate],
  );

  const { data: sections = [], isLoading: sectionsLoading } = useQuery<Section[]>({
    queryKey: ['professor-sections', profile?.id, normalizedSearch, dayFilterValue, courseFilterValue],
    enabled: !!profile?.id && profile?.role === 'professor',
    queryFn: async () => {
      if (!profile?.id) return [];
      const payload = await fetchProfessorSections(profile.id, {
        query: normalizedSearch || undefined,
        dayOfWeek: dayFilterValue,
        courseId: courseFilterValue,
      });
      return (payload ?? []).map((section) => {
        const enrolledCount = section.enrolledCount ?? 0;
        const maxStudents = section.maxStudents ?? 0;
        return {
          ...section,
          enrolledCount,
          maxStudents,
        };
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
    enabled: profile?.role === 'professor',
    queryFn: async () => {
      const payload = await fetchCourses();
      return payload ?? [];
    },
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
        const presentRate =
          session.presentRate ?? (totalStudents > 0 ? presentCount / totalStudents : 0);
        const lateRate = session.lateRate ?? (totalStudents > 0 ? lateCount / totalStudents : 0);
        const attendanceRate = presentRate;
        const attendanceSummary =
          session.attendanceSummary ??
          (totalStudents > 0
            ? `Present ${presentCount}/${totalStudents} • Late ${lateCount}/${totalStudents}`
            : `Present ${presentCount} • Late ${lateCount}`);
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

  const createSectionForm = useForm<SectionFormValues>({
    defaultValues: {
      courseId: '',
      sectionCode: '',
      dayOfWeek: '',
      startTime: '',
      endTime: '',
      location: '',
      maxStudents: undefined,
      lateThresholdMinutes: APP_CONFIG.lateThresholdMinutes,
    },
  });

  const editSectionForm = useForm<SectionFormValues>({
    defaultValues: {
      courseId: '',
      sectionCode: '',
      dayOfWeek: '',
      startTime: '',
      endTime: '',
      location: '',
      maxStudents: undefined,
      lateThresholdMinutes: APP_CONFIG.lateThresholdMinutes,
    },
  });

  const createCourseId = createSectionForm.watch('courseId');
  const createSectionCodeValue = normalizeSectionCodeValue(createSectionForm.watch('sectionCode'));
  const createStartTime = createSectionForm.watch('startTime');
  const createEndTime = createSectionForm.watch('endTime');
  const editCourseId = editSectionForm.watch('courseId');
  const editSectionCodeValue = normalizeSectionCodeValue(editSectionForm.watch('sectionCode'));
  const editStartTime = editSectionForm.watch('startTime');
  const editEndTime = editSectionForm.watch('endTime');
  const sectionBeingEditedCourseId = sectionBeingEdited?.courseId ?? '';
  const sectionBeingEditedCode = normalizeSectionCodeValue(sectionBeingEdited?.sectionCode);

  const createSectionCodeOptions = useMemo(() => {
    const used = createCourseId ? sectionCodesByCourse.get(createCourseId) : undefined;
    return SECTION_CODE_OPTIONS.map((code) => ({
      code,
      disabled: Boolean(used?.has(code)),
    }));
  }, [createCourseId, sectionCodesByCourse]);

  const editSectionCodeOptions = useMemo(() => {
    const courseKey = editCourseId || sectionBeingEditedCourseId;
    const used = courseKey ? sectionCodesByCourse.get(courseKey) : undefined;
    return SECTION_CODE_OPTIONS.map((code) => ({
      code,
      disabled: Boolean(used?.has(code) && code !== editSectionCodeValue),
    }));
  }, [editCourseId, editSectionCodeValue, sectionBeingEditedCourseId, sectionCodesByCourse]);

  const allCreateCodesUsed = useMemo(
    () => createSectionCodeOptions.every((option) => option.disabled),
    [createSectionCodeOptions],
  );

  const allEditCodesUsed = useMemo(
    () => editSectionCodeOptions.every((option) => option.disabled),
    [editSectionCodeOptions],
  );

  useEffect(() => {
    if (!createStartTime || !createEndTime) {
      return;
    }
    void createSectionForm.trigger(['startTime', 'endTime']);
  }, [createStartTime, createEndTime, createSectionForm]);

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
        averagePresentRate:
          payload.averagePresentRate ?? payload.averageAttendanceRate ?? 0,
        averageLateRate: payload.averageLateRate ?? 0,
        averageAttendanceRate:
          payload.averagePresentRate ?? payload.averageAttendanceRate ?? 0,
      };
    },
  });

  const { data: attendanceStats, isLoading: statsLoading } = useQuery<SessionAttendanceStats>({
    queryKey: ['session-attendance-stats', activeSessionId],
    enabled: !!activeSessionId,
    queryFn: async () => {
      if (!activeSessionId) {
        return { total: 0, present: 0, late: 0, absent: 0, pending: 0, manual: 0, automatic: 0 };
      }
      return fetchSessionAttendanceStats(activeSessionId);
    },
  });

  const { data: editRosterData = [], isFetching: editRosterFetching } = useQuery<StudentProfile[]>({
    queryKey: ['section-roster', sectionBeingEdited?.id],
    enabled: editDialogOpen && !!sectionBeingEdited?.id,
    queryFn: async () => {
      if (!sectionBeingEdited?.id) {
        return [];
      }
      const payload = await fetchSectionStudents(sectionBeingEdited.id);
      return (payload ?? []) as StudentProfile[];
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

  const latestAttendanceUpdate = attendanceRecords.length ? attendanceRecords[0] : null;

  const rosterLoading = editDialogOpen && !editRosterHydrated && editRosterFetching;

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
    if (!createDialogOpen) {
      setCreateSelectedStudents([]);
    }
  }, [createDialogOpen]);

  useEffect(() => {
    if (!createCourseId) {
      if (createSectionCodeValue) {
        createSectionForm.setValue('sectionCode', '', { shouldValidate: true, shouldDirty: true });
      }
      return;
    }
    const usedCodes = sectionCodesByCourse.get(createCourseId);
    if (createSectionCodeValue && usedCodes?.has(createSectionCodeValue)) {
      createSectionForm.setValue('sectionCode', '', { shouldValidate: true, shouldDirty: true });
    }
  }, [createCourseId, createSectionCodeValue, createSectionForm, sectionCodesByCourse]);

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
        dayOfWeek: sectionBeingEdited.dayOfWeek != null
          ? String(sectionBeingEdited.dayOfWeek)
          : '',
        startTime: toTimeInput(sectionBeingEdited.startTime),
        endTime: toTimeInput(sectionBeingEdited.endTime),
        location: sectionBeingEdited.location ?? '',
        maxStudents: sectionBeingEdited.maxStudents ?? undefined,
        lateThresholdMinutes:
          sectionBeingEdited.lateThresholdMinutes ?? APP_CONFIG.lateThresholdMinutes,
      });
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

  const createSectionMutation = useMutation({
    mutationFn: async (values: SectionFormValues) => {
      const studentIds = createSelectedStudents
        .map((student) => student.id)
        .filter((id): id is string => Boolean(id));
      const normalizedSectionCode = normalizeSectionCodeValue(values.sectionCode);
      const payload: CreateSectionRequest & { studentIds?: string[] } = {
        courseId: values.courseId,
        sectionCode: normalizedSectionCode,
        dayOfWeek: Number(values.dayOfWeek),
        startTime: values.startTime,
        endTime: values.endTime,
        location: values.location?.trim() || undefined,
        maxStudents: values.maxStudents,
        lateThresholdMinutes: values.lateThresholdMinutes ?? APP_CONFIG.lateThresholdMinutes,
        studentIds: studentIds.length ? studentIds : undefined,
      };
      return createSection(payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['professor-sections', profile?.id] });
      toast({
        title: 'Section created',
        description: 'The section is now available for scheduling sessions.',
      });
      createSectionForm.reset();
      setCreateSelectedStudents([]);
      setCreateDialogOpen(false);
    },
    onError: (error) => handleApiError(error, 'Failed to create section'),
  });

  const editSectionMutation = useMutation({
    mutationFn: async ({ sectionId, request, addIds, removeIds }: SectionUpdatePayload) => {
      const updated = await updateSection(sectionId, request);
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
          };
        });
      }
      queryClient.invalidateQueries({ queryKey: ['professor-sections', profile?.id] });
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
      queryClient.invalidateQueries({ queryKey: ['professor-sections', profile?.id] });
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

  const courseOptions = useMemo(() => {
    return courses
      .filter((course): course is CourseSummary & { id: string } => Boolean(course.id))
      .map((course) => ({
        id: course.id,
        label: `${course.courseCode} - ${course.courseTitle}`,
      }));
  }, [courses]);

  const activeSession = useMemo(() => {
    return orderedSessions.find((session) => session.id === activeSessionId) ?? null;
  }, [orderedSessions, activeSessionId]);

  const openEditSection = useCallback((section: Section) => {
    setSectionBeingEdited(section);
    setEditDialogOpen(true);
    setEditRosterHydrated(false);
  }, []);

  const handleEditDialogChange = useCallback((open: boolean) => {
    setEditDialogOpen(open);
    if (!open) {
      setSectionBeingEdited(null);
      setEditSelectedStudents([]);
      setEditOriginalRoster([]);
      setEditRosterHydrated(false);
    }
  }, []);

  const handleEditSubmit = (values: SectionFormValues) => {
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
    };

    editSectionMutation.mutate({
      sectionId: sectionBeingEdited.id,
      request,
      addIds,
      removeIds,
    });
  };

  const handleConfirmDeleteSection = useCallback(() => {
    if (!sectionBeingEdited?.id || deleteSectionMutation.isPending) {
      return;
    }
    deleteSectionMutation.mutate(sectionBeingEdited.id);
  }, [deleteSectionMutation, sectionBeingEdited?.id]);

  const openSectionDetail = (section: Section) => {
    setActiveSection(section);
  };

  const handleSectionSheetChange = (open: boolean) => {
    if (!open) {
      setActiveSessionId(null);
      setActiveSection(null);
    }
  };

  if (profile?.role !== 'professor') {
    return (
      <div className="p-6">
        <Card>
          <CardContent className="flex flex-col items-center justify-center gap-2 py-10">
            <h3 className="text-lg font-semibold">Access restricted</h3>
            <p className="text-sm text-muted-foreground text-center">
              The sections workspace is available to professors only.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const isCreateSubmitting = createSectionMutation.isPending;
  const isEditSubmitting = editSectionMutation.isPending;
  const isDeletePending = deleteSectionMutation.isPending;

  return (
    <div className="space-y-8 p-6">
      <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="space-y-1">
          <h1 className="text-3xl font-bold tracking-tight text-gradient">My Sections</h1>
          <p className="text-muted-foreground">
            Review section performance, explore session analytics, and manage enrollment-ready schedules.
          </p>
        </div>

        <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button className="btn-gradient shadow-lg shadow-primary/20" size="lg">
              <Plus className="mr-2 h-4 w-4" />
              Add Section
            </Button>
          </DialogTrigger>
          <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-5xl">
            <DialogHeader>
              <DialogTitle>Create a new section</DialogTitle>
              <DialogDescription>
                Link the section to an existing course and schedule-friendly meeting pattern.
              </DialogDescription>
            </DialogHeader>

            <Form {...createSectionForm}>
              <form
                onSubmit={createSectionForm.handleSubmit(
                  (values) => createSectionMutation.mutate(values),
                  handleSectionFormInvalid,
                )}
                className="space-y-8"
              >
                <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
                  <div className="space-y-6">
                    <FormField
                      control={createSectionForm.control}
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
                                  Create a course first
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
                          {courseOptions.length === 0 && !coursesLoading && (
                            <p className="text-xs text-muted-foreground">
                              Add a course before defining sections.
                            </p>
                          )}
                          <FormMessage />
                        </FormItem>
                      )}
                    />

                    <div className="grid gap-4 md:grid-cols-2">
                      <FormField
                        control={createSectionForm.control}
                        name="sectionCode"
                        rules={{ required: 'Section code is required' }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Section code</FormLabel>
                            <Select
                              onValueChange={field.onChange}
                              value={field.value}
                              disabled={!createCourseId || allCreateCodesUsed}
                            >
                              <FormControl>
                                <SelectTrigger>
                                  <SelectValue
                                    placeholder={
                                      createCourseId
                                        ? allCreateCodesUsed
                                          ? 'All section codes in use'
                                          : 'Select section code'
                                        : 'Select a course first'
                                    }
                                  />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                {createSectionCodeOptions.map((option) => (
                                  <SelectItem key={option.code} value={option.code} disabled={option.disabled}>
                                    {option.code}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            {!createCourseId && (
                              <p className="text-xs text-muted-foreground">
                                Select a course to choose from available section codes.
                              </p>
                            )}
                            {createCourseId && allCreateCodesUsed && (
                              <p className="text-xs text-destructive">
                                All section codes are already assigned for this course.
                              </p>
                            )}
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={createSectionForm.control}
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

                    <div className="grid gap-4 md:grid-cols-2">
                      <FormField
                        control={createSectionForm.control}
                        name="startTime"
                        rules={{
                          required: 'Start time is required',
                          validate: (value) => {
                            const message = validateTimeOrder(value, createEndTime);
                            return message ?? true;
                          },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Start time</FormLabel>
                            <FormControl>
                              <Input type="time" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={createSectionForm.control}
                        name="endTime"
                        rules={{
                          required: 'End time is required',
                          validate: (value) => {
                            const message = validateTimeOrder(createStartTime, value);
                            return message ?? true;
                          },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>End time</FormLabel>
                            <FormControl>
                              <Input type="time" {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>

                    <FormField
                      control={createSectionForm.control}
                      name="location"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Location (optional)</FormLabel>
                          <FormControl>
                            <Input placeholder="Room number or meeting link" {...field} />
                          </FormControl>
                        </FormItem>
                      )}
                    />

                    <div className="grid gap-4 md:grid-cols-2">
                      <FormField
                        control={createSectionForm.control}
                        name="maxStudents"
                        rules={{
                          validate: (value) => {
                            const message = validateCapacity(value);
                            return message ?? true;
                          },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Capacity (optional)</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={1}
                                placeholder="Default unlimited"
                                value={field.value ?? ''}
                                onChange={(event) => {
                                  const value = event.target.value;
                                  if (value === '') {
                                    field.onChange(undefined);
                                    createSectionForm.clearErrors('maxStudents');
                                    return;
                                  }
                                  const parsed = Number(value);
                                  if (Number.isNaN(parsed)) {
                                    field.onChange(undefined);
                                    void createSectionForm.trigger('maxStudents');
                                    return;
                                  }
                                  field.onChange(parsed);
                                  void createSectionForm.trigger('maxStudents');
                                }}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={createSectionForm.control}
                        name="lateThresholdMinutes"
                        rules={{
                          required: 'Late threshold is required',
                          min: { value: 0, message: 'Must be 0 minutes or greater' },
                          max: { value: 240, message: 'Must be 240 minutes or less' },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Late threshold (minutes)</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={0}
                                max={240}
                                placeholder={`Default ${APP_CONFIG.lateThresholdMinutes}`}
                                value={field.value ?? ''}
                                onChange={(event) => {
                                  const value = event.target.value;
                                  field.onChange(value === '' ? undefined : Number(value));
                                }}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </div>

                  <div className="space-y-3 rounded-2xl border border-border/60 bg-muted/20 p-4 lg:p-6">
                    <div>
                      <h3 className="text-base font-semibold">Enroll students (optional)</h3>
                      <p className="text-xs text-muted-foreground">
                        Search the directory to attach up to ten students at a time. You can add more later.
                      </p>
                    </div>
                    <StudentSelector
                      selected={createSelectedStudents}
                      onChange={setCreateSelectedStudents}
                      helperText="Search and select students to enroll immediately after creation."
                    />
                  </div>
                </div>

                <div className="flex flex-col-reverse gap-3 lg:flex-row lg:justify-end">
                  <Button
                    type="submit"
                    disabled={isCreateSubmitting || coursesLoading || courseOptions.length === 0}
                    className="btn-gradient w-full lg:w-auto"
                  >
                    {isCreateSubmitting ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Saving section
                      </>
                    ) : (
                      'Create section'
                    )}
                  </Button>
                </div>
              </form>
            </Form>
          </DialogContent>
        </Dialog>

        <Dialog open={editDialogOpen} onOpenChange={handleEditDialogChange}>
          <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-5xl">
            <DialogHeader>
              <DialogTitle>Edit section</DialogTitle>
              <DialogDescription>
                Update schedule details and curate the section roster.
              </DialogDescription>
            </DialogHeader>

            {sectionBeingEdited ? (
              <Form {...editSectionForm}>
                <form
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
                                  <SelectValue placeholder="Select course" />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                {courseOptions.map((course) => (
                                  <SelectItem key={course.id} value={course.id}>
                                    {course.label}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <FormMessage />
                          </FormItem>
                        )}
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
                              disabled={!editCourseId || allEditCodesUsed}
                            >
                              <FormControl>
                                <SelectTrigger>
                                  <SelectValue
                                    placeholder={
                                      editCourseId
                                        ? allEditCodesUsed
                                          ? 'All section codes in use'
                                          : 'Select section code'
                                        : 'Select a course first'
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
                            {!editCourseId && (
                              <p className="text-xs text-muted-foreground">
                                Select a course to choose from available section codes.
                              </p>
                            )}
                            {editCourseId && allEditCodesUsed && (
                              <p className="text-xs text-destructive">
                                All section codes are already assigned for this course.
                              </p>
                            )}
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

                      <div className="grid gap-4 md:grid-cols-2">
                        <FormField
                          control={editSectionForm.control}
                          name="startTime"
                          rules={{
                            required: 'Start time is required',
                            validate: (value) => {
                              const message = validateTimeOrder(value, editEndTime);
                              return message ?? true;
                            },
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Start time</FormLabel>
                              <FormControl>
                                <Input type="time" {...field} />
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
                            validate: (value) => {
                              const message = validateTimeOrder(editStartTime, value);
                              return message ?? true;
                            },
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>End time</FormLabel>
                              <FormControl>
                                <Input type="time" {...field} />
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
                            <FormLabel>Location (optional)</FormLabel>
                            <FormControl>
                              <Input placeholder="Room number or meeting link" {...field} />
                            </FormControl>
                          </FormItem>
                        )}
                      />

                      <div className="grid gap-4 md:grid-cols-2">
                      <FormField
                        control={editSectionForm.control}
                        name="maxStudents"
                        rules={{
                          validate: (value) => {
                            const message = validateCapacity(value);
                            return message ?? true;
                          },
                        }}
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Capacity (optional)</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                min={1}
                                placeholder="Default unlimited"
                                value={field.value ?? ''}
                                onChange={(event) => {
                                  const value = event.target.value;
                                  if (value === '') {
                                    field.onChange(undefined);
                                    editSectionForm.clearErrors('maxStudents');
                                    return;
                                  }
                                  const parsed = Number(value);
                                  if (Number.isNaN(parsed)) {
                                    field.onChange(undefined);
                                    void editSectionForm.trigger('maxStudents');
                                    return;
                                  }
                                  field.onChange(parsed);
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
                            required: 'Late threshold is required',
                            min: { value: 0, message: 'Must be 0 minutes or greater' },
                            max: { value: 240, message: 'Must be 240 minutes or less' },
                          }}
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>Late threshold (minutes)</FormLabel>
                              <FormControl>
                                <Input
                                  type="number"
                                  min={0}
                                  max={240}
                                  placeholder={`Default ${APP_CONFIG.lateThresholdMinutes}`}
                                  value={field.value ?? ''}
                                  onChange={(event) => {
                                    const value = event.target.value;
                                    field.onChange(value === '' ? undefined : Number(value));
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
                        helperText="Search to add or remove students from this section."
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
                          Delete section
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogHeader>
                          <AlertDialogTitle>Delete this section?</AlertDialogTitle>
                          <AlertDialogDescription>
                            Removing the section will permanently delete all session rosters and attendance history and will unenroll every student. This action cannot be undone.
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
                    <Button
                      type="submit"
                      disabled={isEditSubmitting}
                      className="btn-gradient w-full lg:w-auto"
                    >
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
      </header>

      <section className="rounded-3xl border border-border/60 bg-card/70 p-6 shadow-lg shadow-primary/5">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-1 flex-col gap-3 md:flex-row md:items-center">
            <div className="relative max-w-md flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                placeholder="Search by course, code, or section"
                className="pl-9"
              />
            </div>

            <div className="flex flex-1 flex-col gap-3 sm:flex-row">
              <Select value={dayFilter} onValueChange={setDayFilter}>
                <SelectTrigger className="w-full sm:w-48">
                  <SelectValue placeholder="Meeting day">
                    {dayFilter === 'all'
                      ? 'All meeting days'
                      : ISO_WEEKDAYS[Number(dayFilter)]}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All meeting days</SelectItem>
                  {WEEKDAY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <Select value={courseFilter} onValueChange={setCourseFilter}>
                <SelectTrigger className="w-full sm:w-56">
                  <SelectValue placeholder="Course">
                    {courseFilter === 'all'
                      ? 'All courses'
                      : courseOptions.find((course) => course.id === courseFilter)?.label}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All courses</SelectItem>
                  {courseOptions.map((course) => (
                    <SelectItem key={course.id} value={course.id}>
                      {course.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <Badge variant="outline" className="inline-flex items-center gap-2">
            <Filter className="h-3.5 w-3.5" />
            {sections.length} matching sections
          </Badge>
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {sectionsLoading ? (
            Array.from({ length: 3 }).map((_, index) => (
              <Card key={index} className="animate-pulse border-dashed">
                <CardContent className="space-y-4 py-8">
                  <div className="h-6 w-2/3 rounded bg-muted" />
                  <div className="h-4 w-1/2 rounded bg-muted" />
                  <div className="h-4 w-3/4 rounded bg-muted" />
                </CardContent>
              </Card>
            ))
          ) : sections.length === 0 ? (
            <div className="sm:col-span-2 xl:col-span-3">{SECTION_EMPTY_STATE}</div>
          ) : (
            sections.map((section) => {
              const enrollmentLabel = section.enrollmentSummary
                ? section.enrollmentSummary
                : section.maxStudents && section.maxStudents > 0
                  ? `${section.enrolledCount}/${section.maxStudents} seats`
                  : `${section.enrolledCount} students`;
              const dayLabel = section.dayLabel ?? ISO_WEEKDAYS[section.dayOfWeek ?? 1];
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
                          {section.courseCode} &middot; {section.sectionCode}
                        </CardTitle>
                        <CardDescription className="text-sm text-muted-foreground">
                          {section.courseTitle}
                        </CardDescription>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant="secondary" className="uppercase tracking-wide">
                          {dayLabel}
                        </Badge>
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
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-4 w-4" />
                        {timeLabel}
                      </span>
                      {section.location && (
                        <span className="inline-flex items-center gap-1">
                          <MapPin className="h-4 w-4" />
                          {section.location}
                        </span>
                      )}
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
            })
          )}
        </div>
      </section>

      <Sheet open={sectionSheetOpen} onOpenChange={handleSectionSheetChange}>
        <SheetContent side="right" className="flex h-full w-full flex-col overflow-hidden p-0 sm:max-w-full">
          {activeSection && (
            <>
              <div className="absolute right-16 top-4 flex items-center gap-2">
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
              <SheetHeader className="px-4 pt-6 sm:px-6">
                <SheetTitle className="text-left text-2xl font-semibold">
                  {activeSection.courseCode} &middot; {activeSection.sectionCode}
                </SheetTitle>
                <SheetDescription className="text-left">
                  {activeSection.courseTitle}
                </SheetDescription>
              </SheetHeader>

              <ScrollArea className="flex-1 px-4 pb-12 sm:px-6">
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
                        Completed {analyticsSnapshot.completedSessions} &middot; Upcoming {analyticsSnapshot.upcomingSessions}
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
                              <CardTitle className="text-2xl font-semibold">
                                {`${averagePresentPercent}% present`}
                              </CardTitle>
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
                        {activeSection.location && (
                          <div className="flex items-center gap-2">
                            <MapPin className="h-4 w-4" />
                            {activeSection.location}
                          </div>
                        )}
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
                          ? `${activeSection.maxStudents - activeSection.enrolledCount} seats available`
                          : 'No capacity limit set'}
                      </CardContent>
                    </Card>
                  </div>

              <div className="grid gap-6 xl:grid-cols-[1.1fr_1.5fr]">
                <Card className="border-border/50 bg-card/70">
                  <CardHeader>
                    <div className="flex items-center justify-between">
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
                    </div>
                  </CardHeader>
                  <CardContent className="p-0">
                    <ScrollArea className="max-h-[300px] sm:max-h-[420px] xl:max-h-[520px]">
                      <div className="divide-y divide-border/60">
                        {sessionsLoading ? (
                          <div className="flex items-center justify-center gap-2 py-12 text-muted-foreground">
                            <Loader2 className="h-4 w-4 animate-spin" />
                            Loading sessions
                          </div>
                        ) : orderedSessions.length === 0 ? (
                          <div className="py-12 text-center text-sm text-muted-foreground">
                            Sessions scheduled here will appear with live analytics once attendance is captured.
                          </div>
                        ) : (
                          orderedSessions.map((session) => {
                            const denominator = session.totalStudents || session.recordedStudents || 0;
                            const isSelected = activeSessionId === session.id;
                            return (
                              <button
                                key={session.id}
                                type="button"
                                onClick={() => setActiveSessionId(session.id ?? null)}
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
                                  <span>Late: {session.lateCount ?? 0}{denominator ? `/${denominator}` : ''}</span>
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
                    {activeSession && (
                      <CardDescription>
                        {formatDate(activeSession.sessionDate)}
                        {activeSession.timeRangeLabel
                          ? ` &middot; ${activeSession.timeRangeLabel}`
                          : ` &middot; ${formatTimeRange(activeSession.startTime, activeSession.endTime)}`}
                      </CardDescription>
                    )}
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
                              <CardTitle className="text-xl capitalize">
                                {activeSession.status ?? 'pending'}
                              </CardTitle>
                            </CardHeader>
                            <CardContent className="text-xs text-muted-foreground">
                              {activeSession.notes || 'No instructor notes'}
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
                              Present {attendanceSnapshot.present} &middot; Late {attendanceSnapshot.late} &middot; Absent {attendanceSnapshot.absent}
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
                                ? 'Most recent attendance entry'
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
                                  disabled={sessionDownloadState?.sessionId === activeSession.id && sessionDownloadState.format === 'csv'}
                                  onClick={() => handleSessionReportDownload(activeSession.id, 'csv')}
                                >
                                  <Download className="h-4 w-4" />
                                  CSV
                                </Button>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  className="h-8 gap-2"
                                  disabled={sessionDownloadState?.sessionId === activeSession.id && sessionDownloadState.format === 'xlsx'}
                                  onClick={() => handleSessionReportDownload(activeSession.id, 'xlsx')}
                                >
                                  <Download className="h-4 w-4" />
                                  XLSX
                                </Button>
                              </div>
                            </div>
                            <span className="text-xs text-muted-foreground">
                              {attendanceSnapshot.total} records
                            </span>
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
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
};

export default ProfessorSections;



