import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Play,
  Square,
  Users,
  Camera,
  CheckCircle, 
  Clock,
  XCircle,
  AlertCircle,
  RefreshCw,
  Download,
  Smartphone,
  Loader2
} from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useToast } from '@/hooks/use-toast';
import LoadingSpinner from '@/components/LoadingSpinner';
import SessionStartupOverlay, { SessionStartupPhase } from '@/components/live-session/SessionStartupOverlay';
import {
  ApiError,
  fetchProfessorSections,
  fetchSectionSessions,
  fetchSessionAttendance,
  fetchSessionStudents,
  scheduleSession,
  manageSession,
  getCompanionRelease,
  CompanionRelease,
  SectionModelMetadata,
  issueCompanionAccessToken,
  subscribeToSessionEvents,
  downloadCompanionInstaller,
  type AttendanceEvent,
  type RecognitionEvent,
  upsertAttendanceRecord,
  type SessionAttendanceRecord
} from '@/lib/api';
import { formatDateWithOffset } from '@/lib/date-utils';
import { APP_CONFIG } from '@/config/constants';
import {
  detectSupportedDesktopDevice,
  performCompanionHandshake,
  COMPANION_HANDSHAKE_STORAGE_KEY,
  COMPANION_LAST_SESSION_STORAGE_KEY,
  startCompanionSession,
  stopCompanionSession,
  shutdownCompanion
} from '@/lib/companion';
import { buildApiUrl } from '@/lib/openapi-client';

type AttendanceStatus = 'present' | 'absent' | 'late' | 'pending';
type ManualRosterAction = {
  studentId: string;
  status: AttendanceStatus;
  note: string;
};
type SessionStatus = 'scheduled' | 'active' | 'completed' | 'cancelled';

interface SectionSummary {
  id: string;
  courseCode: string;
  courseTitle: string;
  courseDescription?: string;
  sectionCode: string;
  dayOfWeek: number;
  startTime?: string;
  endTime?: string;
  location?: string;
  maxStudents?: number;
  enrolledCount?: number;
  lateThresholdMinutes?: number | null;
}

interface SessionSummary {
  id: string;
  sectionId: string;
  sessionDate: string;
  startTime?: string;
  endTime?: string;
  status: SessionStatus;
  location?: string;
  notes?: string;
  attendanceCount?: number;
  totalStudents?: number;
}

interface AttendanceRecordSummary {
  id: string;
  studentId: string;
  status: AttendanceStatus;
  confidenceScore?: number;
  markedAt?: string | null;
  markingMethod?: string | null;
  student?: {
    fullName?: string;
    studentNumber?: string;
  };
}

interface StudentRoster {
  id: string;
  fullName: string;
  studentNumber?: string;
}

type StudentDto = {
  id: string;
  fullName?: string;
  studentNumber?: string;
  full_name?: string;
  student_id?: string;
};

const normalizeId = (value?: string | null) => (value ?? '').trim().toLowerCase();

const normalizeDayOfWeek = (value?: number | null): number | null => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return null;
  }
  const rounded = Math.round(value);
  if (rounded < 1 || rounded > 7) {
    return null;
  }
  return rounded % 7;
};

const isSameCalendarDay = (left: Date, right: Date): boolean =>
  left.getFullYear() === right.getFullYear() &&
  left.getMonth() === right.getMonth() &&
  left.getDate() === right.getDate();

const normalizeAttendanceStatus = (value?: string | null): AttendanceStatus => {
  switch ((value ?? '').toLowerCase()) {
    case 'present':
      return 'present';
    case 'late':
      return 'late';
    case 'absent':
      return 'absent';
    default:
      return 'pending';
  }
};

interface RecognitionEventSummary {
  id: string;
  timestamp?: string | null;
  studentId?: string | null;
  success: boolean;
  requiresManual?: boolean;
  confidence?: number | null;
  message?: string | null;
  type?: string | null;
  trackId?: string | null;
}

interface SessionStartupState {
  phase: SessionStartupPhase;
  message: string;
  metadata?: SectionModelMetadata | null;
}

const MISSING_FACE_DATA_MESSAGE =
  'Unable to start the live session because no trained face images are available for this section. Capture additional face photos for your students and try again.';

const START_WINDOW_MINUTES = 30;
const MAX_LATE_THRESHOLD_MINUTES = 240;

const toLocalDateString = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const combineDateAndTime = (baseDate: Date, time?: string | null): Date | null => {
  if (!time) {
    return null;
  }
  const trimmed = time.trim();
  if (!trimmed) {
    return null;
  }
  const normalized = trimmed.length === 5 ? `${trimmed}:00` : trimmed;
  const candidate = new Date(`${toLocalDateString(baseDate)}T${normalized}`);
  return Number.isNaN(candidate.getTime()) ? null : candidate;
};

const formatTimeLabel = (date: Date): string =>
  date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

const formatDateLabel = (date: Date): string =>
  date.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });

function resolveStartSessionErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    const message = error.message ?? fallback;
    const normalised = message.toLowerCase();
    if (
      normalised.includes('faces archive missing') ||
      normalised.includes('faces.zip') ||
      normalised.includes('insufficient images') ||
      normalised.includes('no active enrollments') ||
      normalised.includes('cannot retrain') ||
      normalised.includes('retrain section')
    ) {
      return MISSING_FACE_DATA_MESSAGE;
    }
    return message;
  }
  if (error instanceof Error) {
    const trimmed = error.message?.trim();
    return trimmed ? trimmed : fallback;
  }
  return fallback;
}

const LiveSession = () => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [selectedSection, setSelectedSection] = useState<string>('');
  const [isSupportedDesktop, setIsSupportedDesktop] = useState<boolean>(() => detectSupportedDesktopDevice());
  const [companionStatus, setCompanionStatus] = useState<'idle' | 'checking' | 'healthy' | 'unreachable'>(
    'idle'
  );
  const [companionMessage, setCompanionMessage] = useState<string>('');
  const [companionVersion, setCompanionVersion] = useState<string | null>(null);
  const [isProbingCompanion, setIsProbingCompanion] = useState<boolean>(false);
  const [currentTimestamp, setCurrentTimestamp] = useState<number>(() => Date.now());
  const [handshakeToken, setHandshakeToken] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return window.localStorage.getItem(COMPANION_HANDSHAKE_STORAGE_KEY);
  });
  const [sessionStartupState, setSessionStartupState] = useState<SessionStartupState | null>(null);
  const [latestModelMetadata, setLatestModelMetadata] = useState<SectionModelMetadata | null>(null);
  const [attendanceRecords, setAttendanceRecords] = useState<AttendanceRecordSummary[]>([]);
  const [recognitionEvents, setRecognitionEvents] = useState<RecognitionEventSummary[]>([]);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    const interval = window.setInterval(() => setCurrentTimestamp(Date.now()), 30_000);
    return () => window.clearInterval(interval);
  }, []);

  const sanitizeMetadata = useCallback((raw: SectionModelMetadata | null | undefined): SectionModelMetadata | null => {
    if (!raw) {
      return null;
    }

    const missing = Array.isArray(raw.missingStudentIds)
      ? raw.missingStudentIds
          .map(value => {
            if (typeof value === 'string') {
              return value;
            }
            if (value === null || value === undefined) {
              return null;
            }
            try {
              return String(value);
            } catch {
              return null;
            }
          })
          .filter((value): value is string => Boolean(value && value.trim()))
      : [];

    const normalizedCount = (() => {
      if (typeof raw.imageCount === 'number' && Number.isFinite(raw.imageCount)) {
        return raw.imageCount;
      }
      if (raw.imageCount != null) {
        const parsed = Number(raw.imageCount);
        return Number.isFinite(parsed) ? parsed : null;
      }
      return null;
    })();

    const normalizedLabels = (() => {
      if (!raw.labelDisplayNames || typeof raw.labelDisplayNames !== 'object') {
        return null;
      }
      const entries = Object.entries(raw.labelDisplayNames as Record<string, unknown>);
      if (!entries.length) {
        return null;
      }
      const labels: Record<string, string> = {};
      for (const [key, value] of entries) {
        if (typeof value !== 'string') {
          continue;
        }
        const trimmedKey = typeof key === 'string' ? key.trim() : '';
        const trimmedValue = value.trim();
        if (trimmedKey && trimmedValue) {
          labels[trimmedKey] = trimmedValue;
        }
      }
      return Object.keys(labels).length > 0 ? labels : null;
    })();

    return {
      storagePrefix: raw.storagePrefix ?? null,
      imageCount: normalizedCount,
      missingStudentIds: missing,
      modelDownloadPath: raw.modelDownloadPath ?? null,
      labelsDownloadPath: raw.labelsDownloadPath ?? null,
      cascadeDownloadPath: raw.cascadeDownloadPath ?? null,
      labelDisplayNames: normalizedLabels,
    };
  }, []);

  const joinPublicUrl = useCallback((base?: string | null, path?: string | null) => {
    const trimmedBase = base?.trim();
    const trimmedPath = path?.trim();
    if (!trimmedBase || !trimmedPath) {
      return null;
    }
    const normalizedBase = trimmedBase.replace(/\/+$/, '');
    const normalizedPath = trimmedPath.replace(/^\/+/, '');
    if (!normalizedPath) {
      return null;
    }
    if (!normalizedBase) {
      return `/${normalizedPath}`;
    }
    return `${normalizedBase}/${normalizedPath}`;
  }, []);

  const resolveAssetUrl = useCallback((path?: string | null) => {
    if (!path) {
      return null;
    }
    return buildApiUrl(path, { absolute: true });
  }, []);

  const companionReady = companionStatus === 'healthy';
  const isMobileBlocked = !isSupportedDesktop;

  const probeCompanion = useCallback(async () => {
    setIsProbingCompanion(true);
    setCompanionStatus('checking');

    try {
      const result = await performCompanionHandshake();
      if (result.status === 'healthy') {
        setCompanionStatus('healthy');
        setCompanionVersion(result.version ?? null);
        setCompanionMessage(result.message ?? 'Companion app connected.');
        if (result.token) {
          setHandshakeToken(result.token);
        }
      } else {
        setCompanionStatus('unreachable');
        setCompanionVersion(null);
        setCompanionMessage(result.message ?? 'Companion app is not reachable.');
        setHandshakeToken(null);
      }
      return result;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to reach companion app.';
      setCompanionStatus('unreachable');
      setCompanionVersion(null);
      setCompanionMessage(message);
      setHandshakeToken(null);
      return { status: 'unreachable' as const, message };
    } finally {
      setIsProbingCompanion(false);
    }
  }, [
    setCompanionStatus,
    setCompanionVersion,
    setCompanionMessage,
    setHandshakeToken,
    setIsProbingCompanion,
  ]);

  const showApiError = useCallback((error: unknown, fallback: string) => {
    const message = error instanceof ApiError ? error.message : fallback;
    toast({
      title: 'Error',
      description: message,
      variant: 'destructive'
    });
  }, [toast]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const handleResize = () => {
      setIsSupportedDesktop(detectSupportedDesktopDevice());
    };
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (handshakeToken) {
      window.localStorage.setItem(COMPANION_HANDSHAKE_STORAGE_KEY, handshakeToken);
    } else {
      window.localStorage.removeItem(COMPANION_HANDSHAKE_STORAGE_KEY);
    }
  }, [handshakeToken]);

  useEffect(() => {
    if (!isSupportedDesktop) {
      setCompanionStatus('idle');
      setCompanionMessage(
        'Live sessions require a supported laptop or desktop (macOS, Windows, or Linux) with the companion app installed.'
      );
      return;
    }
    if (companionStatus === 'idle') {
      void probeCompanion();
    }
  }, [isSupportedDesktop, companionStatus, probeCompanion]);

  const { data: companionRelease, isLoading: companionReleaseLoading, isFetching: companionReleaseFetching, refetch: refetchCompanionRelease } = useQuery<CompanionRelease>({
    queryKey: ['companion-release'],
    queryFn: getCompanionRelease,
    staleTime: 5 * 60 * 1000,
    retry: false,
    onError: (error) => showApiError(error, 'Failed to load companion app release information')
  });

  type DownloadOption =
    | { type: 'protected'; platform: 'mac' | 'windows' }
    | { type: 'external'; url: string };

  const macDownloadOption = useMemo<DownloadOption | null>(() => {
    if (companionRelease?.macPath) {
      return { type: 'protected', platform: 'mac' };
    }
    const direct = companionRelease?.macUrl?.trim();
    if (direct) {
      return { type: 'protected', platform: 'mac' };
    }
    const fallback = joinPublicUrl(companionRelease?.publicBaseUrl, companionRelease?.macPath);
    if (!fallback) {
      return null;
    }
    const absolute = fallback.startsWith('/') ? buildApiUrl(fallback, { absolute: true }) : fallback;
    return { type: 'external', url: absolute };
  }, [companionRelease?.macPath, companionRelease?.macUrl, companionRelease?.publicBaseUrl, joinPublicUrl]);

  const windowsDownloadOption = useMemo<DownloadOption | null>(() => {
    if (companionRelease?.windowsPath) {
      return { type: 'protected', platform: 'windows' };
    }
    const direct = companionRelease?.windowsUrl?.trim();
    if (direct) {
      return { type: 'protected', platform: 'windows' };
    }
    const fallback = joinPublicUrl(companionRelease?.publicBaseUrl, companionRelease?.windowsPath);
    if (!fallback) {
      return null;
    }
    const absolute = fallback.startsWith('/') ? buildApiUrl(fallback, { absolute: true }) : fallback;
    return { type: 'external', url: absolute };
  }, [companionRelease?.windowsPath, companionRelease?.windowsUrl, companionRelease?.publicBaseUrl, joinPublicUrl]);

  const [downloadingPlatform, setDownloadingPlatform] = useState<'mac' | 'windows' | null>(null);

  const handleInstallerDownload = useCallback(async (platform: 'mac' | 'windows') => {
    try {
      setDownloadingPlatform(platform);
      const { blob, filename } = await downloadCompanionInstaller(platform);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename || (platform === 'mac' ? 'companion-installer-mac' : 'companion-installer-windows');
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      showApiError(error, `Failed to download the ${platform === 'mac' ? 'macOS' : 'Windows'} companion installer`);
    } finally {
      setDownloadingPlatform(null);
    }
  }, [showApiError]);

  const renderDownloadButton = (label: string, option: DownloadOption | null) => {
    if (!option) {
      return (
        <Button size="sm" variant="secondary" disabled>
          <Download className="w-4 h-4 mr-1" />
          {label}
        </Button>
      );
    }

    if (option.type === 'external') {
      return (
        <Button size="sm" variant="secondary" asChild>
          <a href={option.url} target="_blank" rel="noopener noreferrer">
            <Download className="w-4 h-4 mr-1" />
            {label}
          </a>
        </Button>
      );
    }

    const platform = option.platform;
    const isDownloading = downloadingPlatform === platform;

    return (
      <Button
        size="sm"
        variant="secondary"
        onClick={() => void handleInstallerDownload(platform)}
        disabled={isDownloading}
      >
        {isDownloading ? (
          <Loader2 className="w-4 h-4 mr-1 animate-spin" />
        ) : (
          <Download className="w-4 h-4 mr-1" />
        )}
        {isDownloading ? 'Downloading...' : label}
      </Button>
    );
  };

  const { data: sections = [], isLoading: sectionsLoading } = useQuery<SectionSummary[]>({
    queryKey: ['professor-sections', profile?.id],
    queryFn: async () => {
      if (!profile?.id) return [];
      const payload = await fetchProfessorSections(profile.id);
      return payload as SectionSummary[];
    },
    enabled: !!profile?.id && profile?.role === 'professor'
  });

  useEffect(() => {
    if (!selectedSection && sections.length > 0) {
      setSelectedSection(sections[0].id);
    }
  }, [sections, selectedSection]);

  const { data: sectionSessions = [] } = useQuery<SessionSummary[]>({
    queryKey: ['section-sessions', selectedSection],
    queryFn: async () => {
      if (!selectedSection) return [];
      const payload = await fetchSectionSessions(selectedSection);
      return payload as SessionSummary[];
    },
    enabled: !!selectedSection
  });

  const activeSession = useMemo(() => {
    return sectionSessions.find(session => session.status === 'active') ?? null;
  }, [sectionSessions]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (activeSession?.id) {
      window.localStorage.setItem(COMPANION_LAST_SESSION_STORAGE_KEY, activeSession.id);
    } else {
      window.localStorage.removeItem(COMPANION_LAST_SESSION_STORAGE_KEY);
    }
  }, [activeSession?.id]);

  const currentSection = useMemo(() => {
    return sections.find(section => section.id === selectedSection) ?? null;
  }, [sections, selectedSection]);

  const scheduleInfo = useMemo(() => {
    if (!currentSection) {
      return {
        scheduledStart: null as Date | null,
        scheduledEnd: null as Date | null,
        earliestStart: null as Date | null,
        lateThreshold: APP_CONFIG.lateThresholdMinutes,
        isCorrectDay: true,
      };
    }
    const reference = new Date(currentTimestamp);
    const normalizedDay = normalizeDayOfWeek(currentSection.dayOfWeek);
    const baseDate = (() => {
      if (normalizedDay === null) {
        return new Date(reference);
      }
      const anchor = new Date(reference);
      anchor.setHours(0, 0, 0, 0);
      const diff = (normalizedDay - anchor.getDay() + 7) % 7;
      anchor.setDate(anchor.getDate() + diff);
      return anchor;
    })();
    const scheduledStart = combineDateAndTime(baseDate, currentSection.startTime);
    let scheduledEnd = combineDateAndTime(scheduledStart ?? baseDate, currentSection.endTime);
    if (scheduledStart && scheduledEnd && scheduledEnd <= scheduledStart) {
      scheduledEnd = new Date(scheduledEnd.getTime() + 24 * 60 * 60 * 1000);
    }
    const earliestStart = scheduledStart
      ? new Date(scheduledStart.getTime() - START_WINDOW_MINUTES * 60 * 1000)
      : null;
    const rawLateThreshold =
      typeof currentSection.lateThresholdMinutes === 'number'
        ? currentSection.lateThresholdMinutes
        : APP_CONFIG.lateThresholdMinutes;
    const normalizedLateThreshold = Math.round(
      rawLateThreshold ?? APP_CONFIG.lateThresholdMinutes
    );
    const lateThreshold = Math.min(
      MAX_LATE_THRESHOLD_MINUTES,
      Math.max(0, normalizedLateThreshold)
    );
    const isCorrectDay = normalizedDay === null
      ? true
      : scheduledStart
        ? isSameCalendarDay(reference, scheduledStart)
        : reference.getDay() === normalizedDay;
    return { scheduledStart, scheduledEnd, earliestStart, lateThreshold, isCorrectDay };
  }, [currentSection, currentTimestamp]);

  const now = new Date(currentTimestamp);
  const isBeforeStartWindow = scheduleInfo.earliestStart !== null && now < scheduleInfo.earliestStart;
  const isPastScheduledEnd = scheduleInfo.scheduledEnd !== null && now > scheduleInfo.scheduledEnd;
  const isWithinStartWindow = scheduleInfo.isCorrectDay && !isBeforeStartWindow && !isPastScheduledEnd;
  const canStartSession = isSupportedDesktop && companionStatus === 'healthy' && isWithinStartWindow;

  const startDisabledReason = useMemo(() => {
    if (!isSupportedDesktop) {
      return 'Start live sessions from a supported laptop or desktop (macOS, Windows, or Linux) capable of running the companion app.';
    }
    if (companionStatus === 'checking' || isProbingCompanion) {
      return 'Checking companion app connection...';
    }
    if (companionStatus !== 'healthy') {
      return 'Open the companion app and ensure it is running on this device to begin attendance.';
    }
    if (!isWithinStartWindow) {
      if (!scheduleInfo.scheduledStart) {
        return 'Set a meeting start time for this section before starting a live session.';
      }
      if (!scheduleInfo.isCorrectDay) {
        const startAt = scheduleInfo.scheduledStart;
        if (startAt) {
          const dateLabel = startAt.toLocaleDateString([], {
            weekday: 'long',
            month: 'short',
            day: 'numeric',
          });
          const timeLabel = formatTimeLabel(startAt);
          return `Live sessions for this section are available on ${dateLabel} at ${timeLabel}.`;
        }
        return 'Live sessions for this section are only available on the scheduled meeting day.';
      }
      if (isBeforeStartWindow) {
        const windowOpensAt = scheduleInfo.earliestStart ?? scheduleInfo.scheduledStart;
        const startAt = scheduleInfo.scheduledStart;
        const timeLabel = formatTimeLabel(windowOpensAt);
        const scheduledLabel = formatTimeLabel(startAt);
        const dateLabel = formatDateLabel(startAt);
        return `Live sessions open at ${timeLabel} on ${dateLabel} (${START_WINDOW_MINUTES} minutes before the scheduled start at ${scheduledLabel}).`;
      }
      if (isPastScheduledEnd && scheduleInfo.scheduledEnd) {
        const endAt = scheduleInfo.scheduledEnd;
        const endLabel = formatTimeLabel(endAt);
        const dateLabel = formatDateLabel(endAt);
        return `Live sessions must begin before the scheduled end time (${endLabel} on ${dateLabel}).`;
      }
    }
    return null;
  }, [
    isSupportedDesktop,
    companionStatus,
    isProbingCompanion,
    isWithinStartWindow,
    isBeforeStartWindow,
    isPastScheduledEnd,
    scheduleInfo,
  ]);

  const { data: sessionRoster = [] } = useQuery<StudentRoster[]>({
    queryKey: ['session-roster', activeSession?.id],
    queryFn: async () => {
      if (!activeSession?.id) return [];
      const payload = await fetchSessionStudents(activeSession.id) as StudentDto[];
      return payload.map(student => ({
        id: student.id,
        fullName: student.fullName ?? student.full_name ?? 'Unknown Student',
        studentNumber: student.studentNumber ?? student.student_id,
      }));
    },
    enabled: !!activeSession?.id
  });

  const [rosterQuery, setRosterQuery] = useState('');

  const missingStudentSet = useMemo(() => {
    const identifiers = latestModelMetadata?.missingStudentIds ?? [];
    if (!identifiers?.length) {
      return new Set<string>();
    }
    return new Set(identifiers.map(id => normalizeId(id)));
  }, [latestModelMetadata?.missingStudentIds]);

  const missingRosterStudents = useMemo(() => {
    if (!missingStudentSet.size) {
      return [] as StudentRoster[];
    }
    return sessionRoster.filter(student => missingStudentSet.has(normalizeId(student.id)));
  }, [missingStudentSet, sessionRoster]);

  const mapAttendanceRecord = useCallback((record: SessionAttendanceRecord): AttendanceRecordSummary => ({
    id: record.id,
    studentId: record.studentId,
    status: normalizeAttendanceStatus(record.status),
    confidenceScore: typeof record.confidenceScore === 'number' ? record.confidenceScore : undefined,
    markedAt: record.markedAt ?? null,
    markingMethod: record.markingMethod ?? null,
    student: record.student
      ? {
          fullName: record.student.fullName ?? (record.student as { full_name?: string }).full_name ?? undefined,
          studentNumber: record.student.studentNumber
            ?? (record.student as { student_id?: string }).student_id
            ?? undefined,
        }
      : undefined,
  }), []);

  const formatMarkedAt = useCallback((value?: string | null) => {
    if (!value) {
      return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '—';
    }
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }, []);

  const determineRosterStatus = useCallback((): AttendanceStatus => {
    if (!activeSession?.startTime) {
      return 'present';
    }
    const start = new Date(activeSession.startTime);
    if (Number.isNaN(start.getTime())) {
      return 'present';
    }
    const thresholdMinutes = typeof selectedSection?.lateThresholdMinutes === 'number'
      ? Math.max(0, selectedSection.lateThresholdMinutes)
      : 15;
    const cutoff = new Date(start.getTime() + thresholdMinutes * 60_000);
    return Date.now() > cutoff.getTime() ? 'late' : 'present';
  }, [activeSession?.startTime, selectedSection?.lateThresholdMinutes]);

  const filteredRoster = useMemo(() => {
    const query = rosterQuery.trim().toLowerCase();
    if (!query) {
      return sessionRoster;
    }
    return sessionRoster.filter(student => {
      const name = student.fullName?.toLowerCase() ?? '';
      const number = student.studentNumber?.toLowerCase() ?? '';
      const id = student.id.toLowerCase();
      return name.includes(query) || number.includes(query) || id.includes(query);
    });
  }, [rosterQuery, sessionRoster]);

  const manualMarkMutation = useMutation<SessionAttendanceRecord, unknown, ManualRosterAction>({
    mutationFn: async (action: ManualRosterAction) => {
      if (!activeSession?.id) {
        throw new Error('No active session');
      }
      return upsertAttendanceRecord({
        sessionId: activeSession.id,
        studentId: action.studentId,
        status: action.status,
        markingMethod: 'manual',
        notes: action.note,
      });
    },
    onSuccess: (record, variables) => {
      const normalized = mapAttendanceRecord(record);
      setAttendanceRecords(prev => {
        const index = prev.findIndex(item => item.studentId === normalized.studentId);
        if (index >= 0) {
          const next = [...prev];
          next[index] = { ...next[index], ...normalized };
          return next;
        }
        return [...prev, normalized];
      });
      const description = variables?.status === 'absent'
        ? 'Reset to absent from the session roster.'
        : 'Marked manually from the session roster.';
      toast({
        title: 'Attendance updated',
        description,
      });
    },
    onError: (error) => {
      showApiError(error, 'Failed to mark attendance manually');
    },
  });

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    if (!activeSession?.id || !companionReady) {
      setAttendanceRecords([]);
      setRecognitionEvents([]);
      return;
    }

    let cancelled = false;

    const loadInitial = async () => {
      try {
        const payload = await fetchSessionAttendance(activeSession.id);
        if (!cancelled) {
          const records = Array.isArray(payload)
            ? (payload as SessionAttendanceRecord[]).map(mapAttendanceRecord)
            : [];
          setAttendanceRecords(records);
        }
      } catch (error) {
        if (!cancelled) {
          console.error('Failed to load live attendance records', error);
          showApiError(error, 'Failed to load live attendance records');
        }
      }
    };

    loadInitial();
    setRecognitionEvents([]);

    const unsubscribe = subscribeToSessionEvents(activeSession.id, {
      onRecognition: (event: RecognitionEvent) => {
        const rawStudentId = (event as { student_id?: string; studentId?: string }).student_id
          ?? (event as { studentId?: string }).studentId;
        const timestamp = (event as { timestamp?: string }).timestamp
          ?? new Date().toISOString();
        const requiresManual = (event as { requires_manual_confirmation?: boolean; requiresManualConfirmation?: boolean }).requires_manual_confirmation
          ?? (event as { requiresManualConfirmation?: boolean }).requiresManualConfirmation
          ?? false;
        const message = (event as { message?: string | null }).message ?? null;
        const type = (event as { type?: string | null }).type ?? null;
        const trackId = (event as { track_id?: string | null; trackId?: string | null }).track_id
          ?? (event as { trackId?: string | null }).trackId
          ?? null;
        setRecognitionEvents(prev => {
          const entry: RecognitionEventSummary = {
            id: `${timestamp}-${rawStudentId ?? Math.random().toString(36).slice(2)}`,
            timestamp,
            studentId: rawStudentId ?? null,
            success: Boolean(event.success),
            requiresManual: Boolean(requiresManual),
            confidence: typeof event.confidence === 'number' ? event.confidence : null,
            message,
            type,
            trackId,
          };
          const next = [entry, ...prev];
          return next.slice(0, 25);
        });
      },
      onAttendance: (event: AttendanceEvent) => {
        const studentId = (event as { student_id?: string; studentId?: string }).student_id
          ?? (event as { studentId?: string }).studentId;
        if (!studentId) {
          return;
        }
        const normalizedStudentId = normalizeId(studentId);
        let recordUpdated = false;
        setAttendanceRecords(prev => {
          if (!prev.length) {
            return prev;
          }
          let updated = false;
          const next = prev.map(record => {
            if (normalizeId(record.studentId) !== normalizedStudentId) {
              return record;
            }
            updated = true;
            return {
              ...record,
              status: normalizeAttendanceStatus(event.status),
              confidenceScore: typeof event.confidence === 'number'
                ? event.confidence
                : record.confidenceScore,
              markedAt: (event as { timestamp?: string }).timestamp ?? record.markedAt ?? null,
            };
          });
          if (updated) {
            recordUpdated = true;
            return next;
          }
          return prev;
        });
        if (!recordUpdated && activeSession?.id) {
          fetchSessionAttendance(activeSession.id)
            .then((payload) => {
              if (!cancelled) {
                const records = Array.isArray(payload)
                  ? (payload as SessionAttendanceRecord[]).map(mapAttendanceRecord)
                  : [];
                setAttendanceRecords(records);
              }
            })
            .catch((error) => {
              console.warn('Failed to refresh attendance after event', error);
            });
        }
      },
      onError: (evt) => {
        console.warn('Session events stream error', evt);
      },
    });

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [activeSession?.id, companionReady, showApiError, mapAttendanceRecord]);

  const startSessionMutation = useMutation({
    mutationFn: async () => {
      if (!selectedSection) {
        throw new Error('No section selected');
      }

      if (!isWithinStartWindow) {
        throw new Error('Live sessions must begin between 30 minutes before the scheduled start time and the scheduled end time.');
      }

      if (!canStartSession) {
        throw new Error(
          'A supported laptop or desktop with the companion app is required to start a live session.'
        );
      }

      setSessionStartupState({
        phase: 'scheduling',
        message: 'Scheduling attendance session...',
      });

      const probeResult = await probeCompanion();
      if (probeResult.status !== 'healthy') {
        throw new Error(probeResult.message ?? 'Companion app is not reachable.');
      }

      const sessionStartDate = scheduleInfo.scheduledStart ?? new Date();
      const sessionDateString = toLocalDateString(sessionStartDate);
      const startTimeIso = formatDateWithOffset(sessionStartDate);
      const lateThresholdMinutes = scheduleInfo.lateThreshold ?? APP_CONFIG.lateThresholdMinutes;

      const scheduled = await scheduleSession(selectedSection, {
        sessionDate: sessionDateString,
        startTime: startTimeIso,
        lateThresholdMinutes
      }) as { id: string };

      setSessionStartupState((state) =>
        state
          ? {
              ...state,
              phase: 'retraining',
              message: 'Retraining section recognition model...',
            }
          : state
      );

      const sessionDetails = await manageSession(scheduled.id, 'start', profile?.id);
      const metadata = sanitizeMetadata(sessionDetails?.modelMetadata);

      if (!metadata?.storagePrefix) {
        throw new Error(
          'Section retraining did not provide a model storage location. Please verify face data and try again.'
        );
      }

      const nextPhase: SessionStartupPhase = 'syncing';
      const nextMessage = 'Syncing recognition models with the companion app...';

      setSessionStartupState((state) =>
        state
          ? {
              ...state,
              phase: nextPhase,
              message: nextMessage,
              metadata,
            }
          : state
      );

      const possibleTokens = [
        probeResult.token,
        handshakeToken,
        typeof window !== 'undefined'
          ? window.localStorage.getItem(COMPANION_HANDSHAKE_STORAGE_KEY)
          : null,
      ];
      const companionToken = possibleTokens.find((token): token is string => Boolean(token?.length));
      if (!companionToken) {
        throw new Error('Companion handshake token missing. Reconnect the companion app and try again.');
      }

      const modelUrl = resolveAssetUrl(metadata.modelDownloadPath);
      const labelsUrl = resolveAssetUrl(metadata.labelsDownloadPath);
      const cascadeUrl = resolveAssetUrl(metadata.cascadeDownloadPath);
      const backendBaseUrl = sessionDetails?.backendBaseUrl?.trim()
        ? sessionDetails.backendBaseUrl.trim()
        : buildApiUrl('', { absolute: true });

      if (!modelUrl || !labelsUrl || !cascadeUrl) {
        throw new Error('Unable to resolve companion model assets. Please verify the backend is exposing the required downloads.');
      }

      try {
        const accessToken = await issueCompanionAccessToken(sessionDetails.sectionId);

        await startCompanionSession(companionToken, {
          sessionId: scheduled.id,
          sectionId: sessionDetails.sectionId,
          modelUrl,
          cascadeUrl,
          labelsUrl,
          labels: metadata.labelDisplayNames ?? undefined,
          missingStudentIds:
            metadata.missingStudentIds && metadata.missingStudentIds.length > 0
              ? metadata.missingStudentIds
              : undefined,
          authToken: accessToken.token,
          scheduledStart: formatDateWithOffset(sessionStartDate),
          scheduledEnd: scheduleInfo.scheduledEnd
            ? formatDateWithOffset(scheduleInfo.scheduledEnd)
            : undefined,
          lateThresholdMinutes,
          backendBaseUrl,
        });
      } catch (companionError) {
        try {
          await manageSession(scheduled.id, 'stop', profile?.id);
        } catch (rollbackError) {
          console.warn('Failed to roll back session after companion bootstrap error:', rollbackError);
        }

        const rawReason =
          companionError instanceof ApiError
            ? companionError.message
            : companionError instanceof Error
              ? companionError.message
              : String(companionError ?? '');
        const trimmedReason = rawReason?.trim();
        const message = trimmedReason
          ? `Unable to reach the companion application. The session was reverted. Details: ${trimmedReason}`
          : 'Unable to reach the companion application. The session was reverted.';
        const status = companionError instanceof ApiError ? companionError.status : 500;
        const details = companionError instanceof ApiError ? companionError.details : companionError;
        throw new ApiError(message, status, details);
      }

      setSessionStartupState((state) =>
        state
          ? {
              ...state,
              phase: 'finalizing',
              message: 'Finalizing live session...',
              metadata,
            }
          : state
      );

      return { sessionId: scheduled.id, metadata, details: sessionDetails };
    },
    onSuccess: ({ sessionId, metadata }) => {
      if (typeof window !== 'undefined' && sessionId) {
        window.localStorage.setItem(COMPANION_LAST_SESSION_STORAGE_KEY, sessionId);
      }
      queryClient.invalidateQueries({ queryKey: ['section-sessions', selectedSection] });
      toast({
        title: 'Session Started',
        description: 'Attendance session is now active'
      });
      setLatestModelMetadata(metadata ?? null);
    },
    onError: (error) => {
      const friendlyMessage = resolveStartSessionErrorMessage(error, 'Failed to start session');
      setSessionStartupState({
        phase: 'error',
        message: friendlyMessage,
        metadata: null,
      });
      toast({
        title: 'Unable to start session',
        description: friendlyMessage.includes('reverted')
          ? friendlyMessage
          : `${friendlyMessage}. Any changes were reverted because the companion could not be reached.`,
        variant: 'destructive',
      });
    },
    onSettled: (_result, error) => {
      if (!error) {
        setSessionStartupState(null);
      }
    }
  });

  const endSessionMutation = useMutation({
    mutationFn: async () => {
      if (!activeSession?.id) {
        throw new Error('No active session');
      }
      await manageSession(activeSession.id, 'stop', profile?.id);
      if (handshakeToken) {
        try {
          await stopCompanionSession(handshakeToken);
        } catch (error) {
          console.warn('Failed to notify companion about session stop:', error);
        }
        try {
          await shutdownCompanion(handshakeToken);
        } catch (error) {
          console.warn('Failed to shutdown companion application:', error);
        }
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['section-sessions', selectedSection] });
      toast({
        title: 'Session Ended',
        description: 'Attendance session has been completed'
      });
      setLatestModelMetadata(null);
    },
    onError: (error) => showApiError(error, 'Failed to end session')
  });

  const getAttendanceRecord = useCallback(
    (studentId: string) => attendanceRecords.find(r => r.studentId === studentId),
    [attendanceRecords]
  );

  const getAttendanceStatus = useCallback(
    (studentId: string) => getAttendanceRecord(studentId)?.status ?? 'pending',
    [getAttendanceRecord]
  );

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

  const autoRecognitionRecords = attendanceRecords.filter(record => {
    return typeof record.confidenceScore === 'number' && record.confidenceScore > 0;
  });

  const recognitionDisplay = useMemo(() => {
  if (!recognitionEvents.length) {
      return [] as Array<RecognitionEventSummary & { studentName?: string | null; studentNumber?: string | null }>;
    }
    const labelLookup = latestModelMetadata?.labelDisplayNames
      ? Object.entries(latestModelMetadata.labelDisplayNames).reduce((acc, [id, name]) => {
          const key = normalizeId(id);
          if (key && typeof name === 'string' && name.trim().length > 0) {
            acc.set(key, name.trim());
          }
          return acc;
        }, new Map<string, string>())
      : undefined;
    return recognitionEvents.map(event => {
      const rosterMatch = event.studentId
        ? sessionRoster.find(student => normalizeId(student.id) === normalizeId(event.studentId))
        : undefined;
      const normalizedEventId = event.studentId ? normalizeId(event.studentId) : '';
      const fallbackName = normalizedEventId && labelLookup
        ? labelLookup.get(normalizedEventId) ?? null
        : null;
      return {
        ...event,
        studentName: rosterMatch?.fullName ?? fallbackName ?? null,
        studentNumber: rosterMatch?.studentNumber ?? null,
      };
    });
  }, [recognitionEvents, sessionRoster, latestModelMetadata?.labelDisplayNames]);

  const overlayPhase: SessionStartupPhase = sessionStartupState?.phase ?? 'scheduling';
  const overlayMessage = sessionStartupState?.message ?? 'Preparing live session...';
  const overlayMetadata = sessionStartupState?.metadata ?? null;
  const overlayOpen = Boolean(sessionStartupState);
  const handleStartupOverlayDismiss = useCallback(() => {
    setSessionStartupState(null);
  }, []);
  const overlayOnDismiss =
    overlayOpen && sessionStartupState?.phase === 'error' ? handleStartupOverlayDismiss : undefined;

  if (profile?.role !== 'professor') {
    return (
      <>
        <SessionStartupOverlay open={overlayOpen} phase={overlayPhase} message={overlayMessage} metadata={overlayMetadata} />
        <div className="p-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-8">
              <AlertCircle className="w-12 h-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold mb-2">Access Restricted</h3>
              <p className="text-muted-foreground text-center">
                Live session management is only available for professors.
              </p>
            </CardContent>
          </Card>
        </div>
      </>
    );
  }

  if (sectionsLoading) {
    return (
      <>
        <SessionStartupOverlay
          open={overlayOpen}
          phase={overlayPhase}
          message={overlayMessage}
          metadata={overlayMetadata}
          onDismiss={overlayOnDismiss}
        />
        <LoadingSpinner />
      </>
    );
  }

  if (isMobileBlocked) {
    return (
      <>
        <SessionStartupOverlay
          open={overlayOpen}
          phase={overlayPhase}
          message={overlayMessage}
          metadata={overlayMetadata}
          onDismiss={overlayOnDismiss}
        />
        <div className="p-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center gap-4 py-10 text-center">
              <Smartphone className="w-12 h-12 text-muted-foreground" />
              <div className="space-y-2">
                <h3 className="text-lg font-semibold">Use a laptop or desktop</h3>
                <p className="text-sm text-muted-foreground">
                  Live sessions require the companion app running on a supported macOS, Windows, or Linux device.
                  Switch to a laptop or desktop to manage attendance.
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      </>
    );
  }

  return (
    <>
      <SessionStartupOverlay
        open={overlayOpen}
        phase={overlayPhase}
        message={overlayMessage}
        metadata={overlayMetadata}
        onDismiss={overlayOnDismiss}
      />
      <div className="p-4 md:p-6 space-y-6">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold md:text-3xl">Live Session</h1>
            <p className="text-muted-foreground text-sm md:text-base">
              Manage live attendance sessions
            </p>
          </div>
          <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-stretch sm:justify-end">
            <Select value={selectedSection} onValueChange={setSelectedSection}>
              <SelectTrigger className="w-full sm:w-72 lg:w-80">
                <SelectValue placeholder="Select a section" />
              </SelectTrigger>
              <SelectContent>
                {sections.map((section) => (
                  <SelectItem key={section.id} value={section.id}>
                    {section.courseCode} - Section {section.sectionCode}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <div className="flex flex-col items-stretch gap-2 sm:items-end">
              {activeSession ? (
                <Button
                  variant="destructive"
                  onClick={() => endSessionMutation.mutate()}
                  disabled={endSessionMutation.isPending}
                  className="w-full sm:w-auto"
                >
                  <Square className="w-4 h-4 mr-2" />
                  End Session
                </Button>
              ) : (
                <Button
                  onClick={() => startSessionMutation.mutate()}
                  disabled={!selectedSection || startSessionMutation.isPending || !canStartSession}
                  className="w-full sm:w-auto"
                >
                  <Play className="w-4 h-4 mr-2" />
                  Start Session
                </Button>
              )}
              {startDisabledReason && (
                <p className="text-xs text-muted-foreground max-w-xs text-left sm:text-right">
                  {startDisabledReason}
                </p>
              )}
            </div>
          </div>
        </div>

        {isSupportedDesktop && (
          <Card>
            <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Smartphone className="w-5 h-5" />
                  Companion App Manager
                </CardTitle>
                <CardDescription>
                  Ensure the native companion is installed and ready before starting a live session.
                </CardDescription>
              </div>
              <Badge
                variant="secondary"
                className={
                  companionStatus === 'healthy'
                    ? 'bg-green-500 text-white'
                    : companionStatus === 'checking'
                      ? 'bg-amber-500 text-white'
                      : 'bg-red-500 text-white'
                }
              >
                {companionStatus === 'healthy'
                  ? 'Companion Connected'
                  : companionStatus === 'checking'
                    ? 'Checking Companion...'
                    : 'Companion Unavailable'}
              </Badge>
            </CardHeader>
            <CardContent>
              <div className="grid gap-6 md:grid-cols-2">
                <div className="space-y-3">
                  <p className="text-sm text-muted-foreground">
                    {companionMessage || 'Run the companion app on this device to begin automatic attendance.'}
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => probeCompanion()}
                      disabled={isProbingCompanion}
                    >
                      <RefreshCw className={`w-4 h-4 mr-2 ${isProbingCompanion ? 'animate-spin' : ''}`} />
                      Recheck status
                    </Button>
                    {companionVersion && (
                      <span className="text-xs text-muted-foreground">
                        Detected version {companionVersion}
                      </span>
                    )}
                    {handshakeToken && (
                      <span className="text-[10px] text-muted-foreground">
                        Resume token stored locally
                      </span>
                    )}
                  </div>
                </div>
                <div className="space-y-3">
                  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                    <div>
                      <p className="text-sm font-medium">Latest companion build</p>
                      <p className="text-xs text-muted-foreground">
                        {companionReleaseLoading || companionReleaseFetching
                          ? 'Checking for updates...'
                          : companionRelease?.version
                            ? `Version ${companionRelease.version}`
                            : 'No release metadata available yet.'}
                      </p>
                    </div>
                    {companionRelease?.version && (
                      <Badge variant="outline">v{companionRelease.version}</Badge>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {renderDownloadButton('Download macOS', macDownloadOption)}
                    {renderDownloadButton('Download Windows', windowsDownloadOption)}
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => refetchCompanionRelease()}
                      disabled={companionReleaseFetching}
                    >
                      <RefreshCw className={`w-4 h-4 mr-2 ${companionReleaseFetching ? 'animate-spin' : ''}`} />
                      Check updates
                    </Button>
                  </div>
                  {companionRelease?.notes && (
                    <p className="text-xs text-muted-foreground whitespace-pre-line">
                      {companionRelease.notes}
                    </p>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        )}

      {activeSession && currentSection && companionReady && (
        <>
          {/* Session Info */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <Badge variant="default" className="bg-green-500">
                      LIVE
                    </Badge>
                    {currentSection.courseCode} -
                    Section {currentSection.sectionCode}
                  </CardTitle>
                  <CardDescription>
                    {currentSection.courseTitle}
                  </CardDescription>
                </div>
                <div className="flex items-center gap-4">
                  <div className="text-right">
                    <p className="text-sm font-medium">Started</p>
                    <p className="text-sm text-muted-foreground">
                      {activeSession.startTime ? new Date(activeSession.startTime).toLocaleTimeString() : 'Just now'}
                    </p>
                  </div>
                  <Users className="w-6 h-6 text-muted-foreground" />
                </div>
              </div>
            </CardHeader>
            {latestModelMetadata && (
              <CardContent className="grid gap-4 border-t border-border/50 pt-6 sm:grid-cols-3">
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Images used</p>
                  <p className="text-lg font-semibold">
                    {typeof latestModelMetadata.imageCount === 'number'
                      ? latestModelMetadata.imageCount
                      : 'N/A'}
                  </p>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Missing face data</p>
                  <p className="text-lg font-semibold">
                    {missingRosterStudents.length}
                  </p>
                  {missingRosterStudents.length > 0 && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      {missingRosterStudents
                        .map((student) => student.fullName)
                        .filter(Boolean)
                        .join(', ')}
                    </p>
                  )}
                </div>
                <div>
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Model path</p>
                  <p className="text-xs font-mono text-muted-foreground break-all">
                    {latestModelMetadata.storagePrefix ?? 'Unavailable'}
                  </p>
                </div>
              </CardContent>
            )}
          </Card>

          {/* Attendance Grid */}
          <div className="grid gap-6 lg:grid-cols-2">
            {/* Live Attendance */}
            <Card>
              <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Camera className="w-5 h-5" />
                Live Recognition
              </CardTitle>
              <CardDescription>
                Students detected by face recognition
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {recognitionDisplay.length > 0 ? (
                  recognitionDisplay.map((event) => {
                    const defaultStatusLabel = event.success
                      ? (event.requiresManual ? 'Manual confirmation recorded' : 'Marked present automatically')
                      : event.requiresManual
                        ? 'Requires manual confirmation'
                        : 'Recognition attempt failed';
                    const statusLabel =
                      event.message && event.message.trim().length > 0
                        ? event.message.trim()
                        : defaultStatusLabel;
                    const typeLabel = event.type && event.type.trim().length > 0
                      ? event.type.replace(/_/g, ' ').toUpperCase()
                      : null;
                    const icon = event.success
                      ? <CheckCircle className="w-4 h-4 text-green-500" />
                      : event.requiresManual
                        ? <AlertCircle className="w-4 h-4 text-amber-500" />
                        : <XCircle className="w-4 h-4 text-red-500" />;
                    return (
                      <div key={event.id} className="flex items-center justify-between gap-4 rounded-lg border p-3">
                        <div className="flex items-center gap-3">
                          {icon}
                          <div>
                            <p className="font-medium">
                              {event.studentName ?? event.studentId ?? 'Unknown track'}
                            </p>
                            <p className="text-sm text-muted-foreground">{statusLabel}</p>
                            {typeLabel && (
                              <p className="text-[11px] uppercase tracking-wide text-muted-foreground/80">
                                {typeLabel}
                              </p>
                            )}
                            {event.studentNumber && (
                              <p className="text-xs text-muted-foreground">ID: {event.studentNumber}</p>
                            )}
                          </div>
                        </div>
                        <div className="text-right space-y-1">
                          {typeof event.confidence === 'number' && (
                            <p className="text-xs text-muted-foreground">
                              Confidence: {event.confidence.toFixed(2)}
                            </p>
                          )}
                          {event.timestamp && (
                            <p className="text-xs text-muted-foreground">
                              {new Date(event.timestamp).toLocaleTimeString()}
                            </p>
                          )}
                        </div>
                      </div>
                    );
                  })
                ) : (
                  <div className="text-center py-8">
                    <Camera className="w-12 h-12 text-muted-foreground mx-auto mb-4" />
                    <p className="text-muted-foreground">
                      No recognition events yet. Open the native companion app to monitor the live camera feed with bounding boxes.
                    </p>
                  </div>
                )}

                {autoRecognitionRecords.length > 0 && (
                  <div className="mt-4 space-y-2 border-t border-border/60 pt-4">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Latest attendance marks</p>
                    {autoRecognitionRecords.map((record) => (
                      <div key={record.id} className="flex items-center justify-between rounded-lg border p-2">
                        <div className="flex items-center gap-3">
                          {getStatusIcon(record.status)}
                          <div>
                            <p className="text-sm font-medium">{record.student?.fullName ?? 'Student'}</p>
                            {record.student?.studentNumber && (
                              <p className="text-xs text-muted-foreground">ID: {record.student.studentNumber}</p>
                            )}
                          </div>
                        </div>
                        <Badge variant={record.status === 'present' ? 'default' : 'secondary'}>
                          {record.status}
                        </Badge>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

            {/* Manual Attendance */}
            <Card>
              <CardHeader>
                <CardTitle>Session Roster</CardTitle>
                <CardDescription>
                  All enrolled students for this session
                </CardDescription>
              </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <Input
                value={rosterQuery}
                onChange={(event) => setRosterQuery(event.target.value)}
                placeholder="Search students"
                className="max-w-sm"
              />
              <div className="max-h-[480px] space-y-3 overflow-y-auto pr-1">
                {filteredRoster.length === 0 && (
                  <p className="text-sm text-muted-foreground">No students match your search.</p>
                )}
                {filteredRoster.map((roster) => {
                  const record = getAttendanceRecord(roster.id);
                  const status = getAttendanceStatus(roster.id);
                  const missingFaceData = missingStudentSet.has(normalizeId(roster.id));
                  const markedAt = formatMarkedAt(record?.markedAt ?? null);
                  const methodDisplay = record?.markingMethod ? record.markingMethod : '—';
                  const confidenceDisplay = typeof record?.confidenceScore === 'number'
                    ? record.confidenceScore.toFixed(1)
                    : '—';
                  const isMarking = manualMarkMutation.isPending
                    && manualMarkMutation.variables?.studentId === roster.id;
                  const isReset = status === 'present' || status === 'late';
                  const targetStatus = isReset ? 'absent' : determineRosterStatus();
                  const disableManual = isMarking;
                  return (
                    <div key={roster.id} className="rounded-lg border p-3">
                      <div className="flex flex-col gap-3">
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex items-start gap-3">
                            {getStatusIcon(status)}
                            <div>
                              <p className="font-medium">{roster.fullName}</p>
                              <p className="text-sm text-muted-foreground">
                                ID: {roster.studentNumber ?? 'N/A'}
                              </p>
                              {missingFaceData && (
                                <p className="text-xs text-amber-600">No face captures available</p>
                              )}
                            </div>
                          </div>
                          <div className="flex flex-col items-end gap-2">
                            <div className="flex items-center gap-2">
                              {missingFaceData && (
                                <Badge
                                  variant="secondary"
                                  className="border-amber-200 bg-amber-500/10 text-amber-700"
                                >
                                  No face data
                                </Badge>
                              )}
                              <Badge variant={status === 'present' ? 'default' : status === 'late' ? 'secondary' : 'outline'}>
                                {status}
                              </Badge>
                            </div>
                            <Button
                              variant="secondary"
                              size="sm"
                              disabled={disableManual}
                              onClick={() => manualMarkMutation.mutate({
                                studentId: roster.id,
                                status: targetStatus,
                                note: isReset
                                  ? 'Manual roster reset to absent'
                                  : 'Manual roster mark from live session',
                              })}
                            >
                              {isMarking ? (
                                <span className="flex items-center gap-2">
                                  <Loader2 className="h-3 w-3 animate-spin" />
                                  Marking...
                                </span>
                              ) : (
                                isReset ? 'Mark absent' : 'Mark manual'
                              )}
                            </Button>
                          </div>
                        </div>
                        <div className="grid gap-2 text-xs text-muted-foreground sm:grid-cols-3">
                          <span>Marked at: {markedAt}</span>
                          <span>Method: {methodDisplay}</span>
                          <span>Confidence: {confidenceDisplay}</span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </CardContent>
        </Card>
          </div>
        </>
      )}

      {!activeSession && selectedSection && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Play className="w-16 h-16 text-muted-foreground mb-4" />
            <h3 className="text-xl font-semibold mb-2">Ready to Start</h3>
            <p className="text-muted-foreground text-center mb-6">
              Click "Start Session" to begin live attendance tracking for the selected section.
            </p>
          </CardContent>
        </Card>
      )}

      {!selectedSection && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Users className="w-16 h-16 text-muted-foreground mb-4" />
            <h3 className="text-xl font-semibold mb-2">Select a Section</h3>
            <p className="text-muted-foreground text-center">
              Choose a section from the dropdown to start a live attendance session.
            </p>
          </CardContent>
        </Card>
      )}
      </div>
    </>
  );
};

export default LiveSession;









