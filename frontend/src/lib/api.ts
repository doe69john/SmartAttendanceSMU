import { apiRequest, ApiError } from './apiClient';
import { getAccessToken, handleUnauthorized } from './authTokenStore';
import { buildApiUrl, openApiClient, unwrapOpenApiResponse } from './openapi-client';
import type {
  AdminCourseSection,
  AdminCourseStudent,
  AdminCourseSummary,
  AdminSectionSummary,
  ApplicationSettingsResponse,
  AttendanceEvent,
  AttendanceUpsertRequest,
  AuthSessionResponse,
  CourseSummary,
  CreateCourseRequest,
  CreateSectionRequest,
  CurrentUserResponse,
  FaceData,
  FaceDataCreateRequest,
  FaceDataDeleteRequest,
  FaceDataStatus,
  FaceImageFile,
  FaceImageUploadResponse,
  PasscodeValidationResponse,
  PagedResponse,
  ProfessorDashboardSummary,
  ProfessorDirectoryEntry,
  RecognitionEvent,
  RecognitionRequest,
  RecognitionResponse,
  ScheduleSessionRequest,
  SectionEnrollmentRequest,
  SectionSummary,
  SessionActionEvent,
  SessionAttendanceRecord,
  SessionConnectionEvent,
  SessionDetails,
  SessionSummary,
  Student,
  StudentDashboardSummary,
  TrainingResponse,
} from './generated/openapi-types';

export interface AdminUserSummary {
  id: string;
  userId?: string | null;
  fullName?: string | null;
  email?: string | null;
  phone?: string | null;
  role: 'student' | 'professor' | 'admin';
  staffId?: string | null;
  studentId?: string | null;
  active?: boolean | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  faceDataCount?: number | null;
  hasFaceData?: boolean | null;
}

export type AdminUserDetail = AdminUserSummary;

export interface AdminUserUpdatePayload {
  fullName: string;
  email: string;
  phone?: string | null;
  staffId?: string | null;
  studentId?: string | null;
  active?: boolean;
}

export interface SectionModelMetadata {
  storagePrefix?: string | null;
  imageCount?: number | null;
  missingStudentIds?: string[] | null;
  modelDownloadPath?: string | null;
  labelsDownloadPath?: string | null;
  cascadeDownloadPath?: string | null;
  labelDisplayNames?: Record<string, string> | null;
}

export type SessionDetailsWithModel = SessionDetails & {
  modelMetadata?: SectionModelMetadata | null;
};

export interface CompanionAccessTokenResponse {
  token: string;
  expiresAt: string;
  backendBaseUrl?: string | null;
}

export interface SectionAnalytics {
  totalSessions: number;
  completedSessions: number;
  upcomingSessions: number;
  averageAttendanceRate: number;
  averagePresentRate: number;
  averageLateRate: number;
}

/**
 * Mirrors {@link com.smartattendance.supabase.dto.CompanionReleaseResponse}.
 * The backend owns manifest parsing logic so the web app simply consumes the
 * DTO shape without duplicating business rules in TypeScript.
 */
export type CompanionRelease = {
  version: string;
  notes?: string | null;
  macUrl?: string | null;
  windowsUrl?: string | null;
  publishedAt?: string | null;
  macPath?: string | null;
  windowsPath?: string | null;
  bucket?: string | null;
  publicBaseUrl?: string | null;
};

export interface SessionAttendanceStats {
  total: number;
  present: number;
  late: number;
  absent: number;
  pending: number;
  automatic: number;
  manual: number;
}

export type SessionAttendanceRecordView = SessionAttendanceRecord & {
  markingMethod?: string | null;
};

export interface ProfessorStudentReport {
  student: Student;
  sectionCount: number;
  courseCount: number;
  totalSessions: number;
  attendedSessions: number;
  missedSessions: number;
  recordedSessions: number;
  attendanceRate: number;
  lastAttendanceAt?: string | null;
}

export interface StudentSectionReport {
  sectionId: string;
  courseId: string;
  courseCode: string;
  courseTitle: string;
  sectionCode: string;
  totalSessions: number;
  attendedSessions: number;
  missedSessions: number;
  recordedSessions: number;
  attendanceRate: number;
}

export interface StudentAttendanceHistory {
  sessionId: string;
  sectionId: string;
  sectionCode: string;
  courseCode: string;
  courseTitle: string;
  sessionDate?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  status?: string | null;
  markedAt?: string | null;
  markingMethod?: string | null;
  location?: string | null;
  notes?: string | null;
}

export interface StudentReportDetail {
  summary: ProfessorStudentReport;
  sections: StudentSectionReport[];
  attendanceHistory: StudentAttendanceHistory[];
}

export interface StudentSectionDetail {
  section: SectionSummary;
  attendanceHistory: StudentAttendanceHistory[];
}

export interface ProfessorSectionReport {
  sectionId: string;
  courseId: string;
  courseCode: string;
  courseTitle: string;
  sectionCode: string;
  location?: string | null;
  enrolledStudents: number;
  maxStudents: number;
  totalSessions: number;
  completedSessions: number;
  upcomingSessions: number;
  averageAttendanceRate: number;
}

export interface SectionReportDetail {
  summary: ProfessorSectionReport;
  sessions: SessionSummary[];
}

export interface ReportDownload {
  blob: Blob;
  filename: string;
  contentType: string;
}

export interface RosterImportIssue {
  rowNumber?: number | null;
  value?: string | null;
  reason: string;
}

export interface RosterImportSummary {
  processedCount: number;
  matchedCount: number;
  duplicateCount: number;
  students: Student[];
  issues: RosterImportIssue[];
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type Headers = Record<string, string>;

async function request<T>(path: string, method: HttpMethod, body?: unknown, headers: Headers = {}): Promise<T> {
  return apiRequest<T>({
    path,
    method,
    body,
    headers,
  });
}

async function authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers(init.headers ?? {});

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', 'Bearer ' + token);
  }

  if (!headers.has('Accept')) {
    headers.set('Accept', '*/*');
  }

  const url = buildApiUrl(path);
  const response = await fetch(url, {
    ...init,
    headers,
    credentials: 'include',
  });

  if (response.status === 401) {
    await handleUnauthorized();
    throw new ApiError('Unauthorized', response.status);
  }

  if (!response.ok) {
    throw new ApiError(response.statusText || 'Request failed', response.status);
  }

  return response;
}

function extractFilename(response: Response, fallback: string): string {
  const disposition = response.headers.get('Content-Disposition');
  if (disposition) {
    const match = disposition.match(/filename="?([^";]+)"?/i);
    if (match?.[1]) {
      const sanitized = sanitizeDownloadFilename(match[1]);
      if (sanitized) {
        return sanitized;
      }
    }
  }
  return fallback;
}

export { ApiError };

export type {
  AdminCourseSection,
  AdminCourseStudent,
  AdminCourseSummary,
  ApplicationSettingsResponse,
  AttendanceEvent,
  AttendanceUpsertRequest,
  AuthSessionResponse,
  CourseSummary,
  CreateCourseRequest,
  CreateSectionRequest,
  CurrentUserResponse,
  FaceData,
  FaceDataCreateRequest,
  FaceDataDeleteRequest,
  FaceDataStatus,
  FaceImageFile,
  FaceImageUploadResponse,
  PasscodeValidationResponse,
  PagedResponse,
  ProfessorDashboardSummary,
  ProfessorDirectoryEntry,
  RecognitionEvent,
  RecognitionRequest,
  RecognitionResponse,
  ScheduleSessionRequest,
  SectionEnrollmentRequest,
  SectionSummary,
  SessionActionEvent,
  SessionAttendanceRecord,
  SessionConnectionEvent,
  SessionDetails,
  SessionSummary,
  Student,
  StudentDashboardSummary,
  TrainingResponse,
  SectionAnalytics,
  SessionAttendanceStats,
  SessionAttendanceRecordView,
  ProfessorStudentReport,
  StudentSectionReport,
  StudentAttendanceHistory,
  StudentReportDetail,
  ProfessorSectionReport,
  SectionReportDetail,
  ReportDownload,
};

export interface AttendanceQueryParams {
  sessionId?: string;
  sectionId?: string;
  studentId?: string;
  statuses?: string[];
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface FaceCaptureAnalysisResponse {
  valid: boolean;
  faceCount: number;
  message?: string | null;
  sharpness: number;
  brightness: number;
  boundingBoxes?: Array<{ x: number; y: number; width: number; height: number }>;
}

export interface FaceImageUploadOptions {
  upsert?: boolean;
  frameWidth?: number;
  frameHeight?: number;
  boundingBox?: { x: number; y: number; width: number; height: number } | null;
}

export interface SessionEventHandlers {
  onInit?: (event: SessionConnectionEvent) => void;
  onRecognition?: (event: RecognitionEvent) => void;
  onAttendance?: (event: AttendanceEvent) => void;
  onSessionAction?: (event: SessionActionEvent) => void;
  onUnknownEvent?: (event: MessageEvent) => void;
  onError?: (event: Event) => void;
}

function parseEventData<T>(event: MessageEvent): T | null {
  try {
    return JSON.parse(event.data) as T;
  } catch (error) {
    console.warn('Failed to parse SSE payload', error);
    return null;
  }
}

export function subscribeToSessionEvents(sessionId: string, handlers: SessionEventHandlers = {}) {
  const controller = new AbortController();
  const url = buildApiUrl(`/sessions/${sessionId}/events`);
  const token = getAccessToken();
  const headers = new Headers({ Accept: 'text/event-stream' });

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const dispatchMessage = (eventName: string, data: string) => {
    const messageEvent = new MessageEvent(eventName, { data });

    switch (eventName) {
      case 'recognition': {
        const payload = parseEventData<RecognitionEvent>(messageEvent);
        if (payload) {
          handlers.onRecognition?.(payload);
        } else {
          handlers.onUnknownEvent?.(messageEvent);
        }
        break;
      }
      case 'attendance': {
        const payload = parseEventData<AttendanceEvent>(messageEvent);
        if (payload) {
          handlers.onAttendance?.(payload);
        } else {
          handlers.onUnknownEvent?.(messageEvent);
        }
        break;
      }
      case 'session-action': {
        const payload = parseEventData<SessionActionEvent>(messageEvent);
        if (payload) {
          handlers.onSessionAction?.(payload);
        } else {
          handlers.onUnknownEvent?.(messageEvent);
        }
        break;
      }
      case 'init': {
        const payload = parseEventData<SessionConnectionEvent>(messageEvent);
        if (payload) {
          handlers.onInit?.(payload);
        } else {
          handlers.onUnknownEvent?.(messageEvent);
        }
        break;
      }
      default: {
        handlers.onUnknownEvent?.(messageEvent);
        break;
      }
    }
  };

  const pump = async () => {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers,
        signal: controller.signal,
        credentials: 'include',
      });

      if (response.status === 401) {
        handleUnauthorized();
        throw new Error('Unauthorized session stream');
      }

      if (!response.ok || !response.body) {
        throw new Error(`Failed to subscribe to session events: ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      let finished = false;

      while (!controller.signal.aborted) {
        const { value, done } = await reader.read();
        if (done) {
          finished = true;
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        let separatorIndex = buffer.indexOf('\n\n');
        while (separatorIndex !== -1) {
          const rawEvent = buffer.slice(0, separatorIndex);
          buffer = buffer.slice(separatorIndex + 2);

          let eventName = 'message';
          const dataLines: string[] = [];

          for (const line of rawEvent.split('\n')) {
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              dataLines.push(line.slice(5).trim());
            }
          }

          dispatchMessage(eventName, dataLines.join('\n'));
          separatorIndex = buffer.indexOf('\n\n');
        }
      }

      buffer += decoder.decode();

      let separatorIndex = buffer.indexOf('\n\n');
      while (separatorIndex !== -1) {
        const rawEvent = buffer.slice(0, separatorIndex);
        buffer = buffer.slice(separatorIndex + 2);

        let eventName = 'message';
        const dataLines: string[] = [];

        for (const line of rawEvent.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trim());
          }
        }

        dispatchMessage(eventName, dataLines.join('\n'));
        separatorIndex = buffer.indexOf('\n\n');
      }

      if (!finished) {
        await reader.cancel();
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        console.error('Session SSE subscription failed', error);
        handlers.onError?.(new Event('error'));
      }
    }
  };

  void pump();

  return () => {
    controller.abort();
  };
}

export async function fetchApplicationSettings() {
  const result = await openApiClient.GET('/api/settings');
  return unwrapOpenApiResponse(result, 'Failed to load application settings');
}

export async function validateStaffPasscode(passcode: string) {
  const result = await openApiClient.POST('/api/settings/validate-staff-passcode', {
    body: { passcode },
  });
  return unwrapOpenApiResponse(result, 'Invalid passcode');
}

export async function validateAdminPasscode(passcode: string) {
  const result = await openApiClient.POST('/api/settings/validate-admin-passcode', {
    body: { passcode },
  });
  return unwrapOpenApiResponse(result, 'Invalid passcode');
}

export async function fetchCurrentUser() {
  const result = await openApiClient.GET('/api/me');
  return unwrapOpenApiResponse(result, 'Failed to load current user');
}

export async function signIn(email: string, password: string) {
  const result = await openApiClient.POST('/api/auth/sign-in', {
    body: { email, password },
  });
  return unwrapOpenApiResponse(result, 'Unable to sign in');
}

export async function signOut() {
  const result = await openApiClient.POST('/api/auth/sign-out');
  unwrapOpenApiResponse(result, 'Unable to sign out');
}

export async function signUp(email: string, password: string, userData: Record<string, unknown>) {
  const result = await openApiClient.POST('/api/auth/sign-up', {
    body: { email, password, userData },
  });
  return unwrapOpenApiResponse(result, 'Unable to register account');
}

export async function requestPasswordReset(email: string) {
  const result = await openApiClient.POST('/api/auth/password-reset', {
    body: { email },
  });
  return unwrapOpenApiResponse(result, 'Unable to send password reset email');
}

export async function confirmPasswordReset(accessToken: string, password: string) {
  const result = await openApiClient.POST('/api/auth/password-reset/confirm', {
    body: { accessToken, password },
  });
  return unwrapOpenApiResponse(result, 'Unable to update password');
}

export async function manageSession(
  sessionId: string,
  action: 'start' | 'pause' | 'resume' | 'stop',
  professorId?: string,
) {
  const payload = professorId ? { professorId } : undefined;
  return request<SessionDetailsWithModel>(`/sessions/${sessionId}/${action}`, 'POST', payload);
}

export async function recognizeFace(payload: RecognitionRequest) {
  return request<RecognitionResponse>('/recognition', 'POST', payload);
}

export async function startTraining(studentId: string, trainingImages: string[], testImages: string[]) {
  return request<TrainingResponse>('/recognition/train', 'POST', {
    studentId,
    trainingImages,
    testImages,
  });
}

export async function patchAttendanceRecord(recordId: string, updates: Partial<AttendanceUpsertRequest>) {
  return request<SessionAttendanceRecord>(`/attendance/${recordId}`, 'PATCH', updates);
}

export async function upsertAttendanceRecord(payload: AttendanceUpsertRequest) {
  return request<SessionAttendanceRecord>('/attendance', 'POST', payload);
}

export async function fetchSession(sessionId: string) {
  return request<SessionDetailsWithModel>(`/sessions/${sessionId}`, 'GET');
}

export async function fetchSessionAttendance(sessionId: string) {
  return request<SessionAttendanceRecordView[]>(`/sessions/${sessionId}/attendance`, 'GET');
}

export async function fetchSessionAttendanceStats(sessionId: string) {
  return request<SessionAttendanceStats>(`/sessions/${sessionId}/stats`, 'GET');
}

export async function fetchSectionAnalytics(sectionId: string) {
  return request<SectionAnalytics>(`/sections/${sectionId}/analytics`, 'GET');
}

export async function fetchSessionStudents(sessionId: string) {
  return request<Student[]>(`/sessions/${sessionId}/students`, 'GET');
}

export async function fetchAttendance(params: AttendanceQueryParams) {
  const searchParams = new URLSearchParams();

  if (params.sessionId) {
    searchParams.set('session', params.sessionId);
  }

  if (params.studentId) {
    searchParams.set('student', params.studentId);
  }

  if (params.sectionId) {
    searchParams.set('section', params.sectionId);
  }

  params.statuses?.forEach(status => searchParams.append('status', status));

  if (params.from) {
    searchParams.set('from', params.from);
  }

  if (params.to) {
    searchParams.set('to', params.to);
  }

  if (typeof params.page === 'number') {
    searchParams.set('page', params.page.toString());
  }

  if (typeof params.size === 'number') {
    searchParams.set('size', params.size.toString());
  }

  const query = searchParams.toString();
  return request<PagedResponse<SessionAttendanceRecord>>(`/attendance${query ? `?${query}` : ''}`, 'GET');
}

export async function fetchProfessorDashboard() {
  const result = await openApiClient.GET('/api/dashboard/professor');
  return unwrapOpenApiResponse(result, 'Failed to load dashboard');
}

export async function fetchStudentDashboard() {
  const result = await openApiClient.GET('/api/dashboard/student');
  return unwrapOpenApiResponse(result, 'Failed to load dashboard');
}

export async function fetchCourses() {
  const result = await openApiClient.GET('/api/courses');
  return unwrapOpenApiResponse(result, 'Failed to load courses');
}

export async function fetchAdminCourses(params: { query?: string } = {}) {
  const normalized = params.query?.trim();
  const query = normalized ? `?q=${encodeURIComponent(normalized)}` : '';
  return request<AdminCourseSummary[]>(`/admin/courses${query}`, 'GET');
}

export async function createCourse(payload: CreateCourseRequest) {
  const result = await openApiClient.POST('/api/courses', { body: payload });
  return unwrapOpenApiResponse(result, 'Failed to create course');
}

export async function updateCourse(courseId: string, payload: CreateCourseRequest) {
  const result = await openApiClient.PUT('/api/courses/{id}', {
    params: { path: { id: courseId } },
    body: payload,
  });
  return unwrapOpenApiResponse(result, 'Failed to update course');
}

export async function deleteCourse(courseId: string) {
  const result = await openApiClient.DELETE('/api/courses/{id}', {
    params: { path: { id: courseId } },
  });
  unwrapOpenApiResponse(result, 'Failed to delete course');
}

export async function fetchAdminCourseSections(
  courseId: string,
  params: { query?: string } = {},
) {
  const normalized = params.query?.trim();
  const query = normalized ? `?q=${encodeURIComponent(normalized)}` : '';
  const base = `/admin/courses/${encodeURIComponent(courseId)}/sections`;
  return request<AdminCourseSection[]>(`${base}${query}`, 'GET');
}

export async function fetchAdminCourseStudents(
  courseId: string,
  params: { query?: string } = {},
) {
  const normalized = params.query?.trim();
  const query = normalized ? `?q=${encodeURIComponent(normalized)}` : '';
  const base = `/admin/courses/${encodeURIComponent(courseId)}/students`;
  return request<AdminCourseStudent[]>(`${base}${query}`, 'GET');
}

export async function fetchAdminSections(params: { query?: string } = {}) {
  const normalized = params.query?.trim();
  const query = normalized ? `?q=${encodeURIComponent(normalized)}` : '';
  return request<AdminSectionSummary[]>(`/admin/sections${query}`, 'GET');
}

export async function fetchAdminUsers(params: { role?: 'professor' | 'student'; query?: string; limit?: number } = {}) {
  const searchParams = new URLSearchParams();
  if (params.role) {
    searchParams.set('role', params.role);
  }
  const normalized = params.query?.trim();
  if (normalized) {
    searchParams.set('q', normalized);
  }
  if (typeof params.limit === 'number' && Number.isFinite(params.limit)) {
    searchParams.set('limit', String(params.limit));
  }
  const query = searchParams.toString();
  return request<AdminUserSummary[]>(`/admin/users${query ? `?${query}` : ''}`, 'GET');
}

export async function updateAdminUser(profileId: string, payload: AdminUserUpdatePayload) {
  return request<AdminUserDetail>(`/admin/users/${profileId}`, 'PUT', payload);
}

export async function deleteAdminUser(profileId: string) {
  return request<void>(`/admin/users/${profileId}`, 'DELETE');
}

export async function fetchAdminSection(sectionId: string) {
  return request<AdminSectionSummary>(`/admin/sections/${sectionId}`, 'GET');
}

export async function createAdminSection(payload: CreateSectionRequest & { studentIds?: string[] }) {
  const { studentIds, ...rest } = payload;
  const body = {
    ...rest,
    ...(Array.isArray(studentIds) && studentIds.length > 0 ? { student_ids: studentIds } : {}),
  };
  return request<SectionSummary>('/admin/sections', 'POST', body);
}

export async function updateAdminSection(sectionId: string, payload: CreateSectionRequest) {
  return request<SectionSummary>(`/admin/sections/${sectionId}`, 'PUT', payload);
}

export async function fetchProfessorCourses(professorId: string) {
  const result = await openApiClient.GET('/api/professors/{id}/courses', {
    params: { path: { id: professorId } },
  });
  return unwrapOpenApiResponse(result, 'Failed to load courses');
}

export async function fetchProfessorSessions(professorId: string) {
  const result = await openApiClient.GET('/api/professors/{id}/sessions', {
    params: { path: { id: professorId } },
  });
  return unwrapOpenApiResponse(result, 'Failed to load sessions');
}

export async function fetchProfessorSections(
  professorId: string,
  options: { query?: string; dayOfWeek?: number; courseId?: string } = {},
) {
  const searchParams = new URLSearchParams();
  if (options.query) {
    searchParams.set('query', options.query);
  }
  if (options.dayOfWeek !== undefined && options.dayOfWeek !== null) {
    searchParams.set('dayOfWeek', String(options.dayOfWeek));
  }
  if (options.courseId) {
    searchParams.set('courseId', options.courseId);
  }
  const query = searchParams.toString();
  return request<SectionSummary[]>(
    `/professors/${professorId}/sections${query ? `?${query}` : ''}`,
    'GET',
  );
}

export async function fetchStudentSections(studentId: string) {
  const result = await openApiClient.GET('/api/students/{id}/sections', {
    params: { path: { id: studentId } },
  });
  return unwrapOpenApiResponse(result, 'Failed to load sections') as SectionSummary[];
}

export async function fetchStudentSectionDetail(studentId: string, sectionId: string) {
  return request<StudentSectionDetail>(`/students/${studentId}/sections/${sectionId}`, 'GET');
}

export async function fetchSectionSessions(sectionId: string) {
  return request<SessionSummary[]>(`/sections/${sectionId}/sessions`, 'GET');
}

export async function getCompanionRelease() {
  return request<CompanionRelease>('/companion/releases/latest', 'GET');
}

export async function fetchProfessorStudentReports(params: { query?: string } = {}) {
  const normalized = params.query?.trim();
  const query = normalized ? `?query=${encodeURIComponent(normalized)}` : '';
  return request<ProfessorStudentReport[]>(`/reports/professor/students${query}`, 'GET');
}

export async function fetchAdminStudentReports(params: { query?: string } = {}) {
  const normalized = params.query?.trim();
  const query = normalized ? `?query=${encodeURIComponent(normalized)}` : '';
  return request<ProfessorStudentReport[]>(`/reports/admin/students${query}`, 'GET');
}

export async function fetchStudentReportDetail(studentId: string) {
  return request<StudentReportDetail>(`/reports/professor/students/${studentId}`, 'GET');
}

export async function fetchAdminStudentReportDetail(studentId: string) {
  return request<StudentReportDetail>(`/reports/admin/students/${studentId}`, 'GET');
}

export async function fetchProfessorSectionReports(params: { query?: string } = {}) {
  const normalized = params.query?.trim();
  const query = normalized ? `?query=${encodeURIComponent(normalized)}` : '';
  return request<ProfessorSectionReport[]>(`/reports/professor/sections${query}`, 'GET');
}

export async function fetchAdminSectionReports(params: { query?: string } = {}) {
  const normalized = params.query?.trim();
  const query = normalized ? `?query=${encodeURIComponent(normalized)}` : '';
  return request<ProfessorSectionReport[]>(`/reports/admin/sections${query}`, 'GET');
}

export async function fetchSectionReportDetail(sectionId: string) {
  return request<SectionReportDetail>(`/reports/professor/sections/${sectionId}`, 'GET');
}

export async function fetchAdminSectionReportDetail(sectionId: string) {
  return request<SectionReportDetail>(`/reports/admin/sections/${sectionId}`, 'GET');
}

type SectionReportDownloadOptions =
  | 'csv'
  | 'xlsx'
  | { format?: 'csv' | 'xlsx'; sessionId?: string };

export async function downloadSectionReport(
  sectionId: string,
  formatOrOptions: SectionReportDownloadOptions = 'xlsx',
): Promise<ReportDownload> {
  const { format, sessionId } =
    typeof formatOrOptions === 'string'
      ? { format: formatOrOptions, sessionId: undefined }
      : { format: formatOrOptions.format ?? 'xlsx', sessionId: formatOrOptions.sessionId };

  const normalized = format === 'csv' ? 'csv' : 'xlsx';
  const searchParams = new URLSearchParams();
  searchParams.set('format', normalized);
  if (sessionId) {
    searchParams.set('sessionId', sessionId);
  }
  const query = `?${searchParams.toString()}`;
  const accept = normalized === 'csv'
    ? 'text/csv'
    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  const response = await authorizedFetch(`/reports/professor/sections/${sectionId}/export${query}`, {
    headers: { Accept: accept },
  });
  const blob = await response.blob();
  return {
    blob,
    filename: extractFilename(response, `section-${sectionId}.${normalized}`),
    contentType: response.headers.get('Content-Type') ?? accept,
  };
}

export async function downloadStudentReport(studentId: string, format: 'csv' | 'xlsx' = 'xlsx'): Promise<ReportDownload> {
  const normalized = format === 'csv' ? 'csv' : 'xlsx';
  const query = `?format=${encodeURIComponent(normalized)}`;
  const accept = normalized === 'csv'
    ? 'text/csv'
    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  const response = await authorizedFetch(`/reports/professor/students/${studentId}/export${query}`, {
    headers: { Accept: accept },
  });
  const blob = await response.blob();
  return {
    blob,
    filename: extractFilename(response, `student-${studentId}.${normalized}`),
    contentType: response.headers.get('Content-Type') ?? accept,
  };
}

export async function downloadAdminSectionReport(
  sectionId: string,
  formatOrOptions: SectionReportDownloadOptions = 'xlsx',
): Promise<ReportDownload> {
  const { format, sessionId } =
    typeof formatOrOptions === 'string'
      ? { format: formatOrOptions, sessionId: undefined }
      : { format: formatOrOptions.format ?? 'xlsx', sessionId: formatOrOptions.sessionId };

  const normalized = format === 'csv' ? 'csv' : 'xlsx';
  const searchParams = new URLSearchParams();
  searchParams.set('format', normalized);
  if (sessionId) {
    searchParams.set('sessionId', sessionId);
  }
  const query = `?${searchParams.toString()}`;
  const accept = normalized === 'csv'
    ? 'text/csv'
    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  const response = await authorizedFetch(`/reports/admin/sections/${sectionId}/export${query}`, {
    headers: { Accept: accept },
  });
  const blob = await response.blob();
  return {
    blob,
    filename: extractFilename(response, `section-${sectionId}.${normalized}`),
    contentType: response.headers.get('Content-Type') ?? accept,
  };
}

export async function downloadAdminStudentReport(studentId: string, format: 'csv' | 'xlsx' = 'xlsx'): Promise<ReportDownload> {
  const normalized = format === 'csv' ? 'csv' : 'xlsx';
  const query = `?format=${encodeURIComponent(normalized)}`;
  const accept = normalized === 'csv'
    ? 'text/csv'
    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  const response = await authorizedFetch(`/reports/admin/students/${studentId}/export${query}`, {
    headers: { Accept: accept },
  });
  const blob = await response.blob();
  return {
    blob,
    filename: extractFilename(response, `student-${studentId}.${normalized}`),
    contentType: response.headers.get('Content-Type') ?? accept,
  };
}

export async function downloadMyAttendanceReport(options: { format?: 'csv' | 'xlsx'; sectionId?: string } = {}): Promise<ReportDownload> {
  const normalized = options.format === 'csv' ? 'csv' : 'xlsx';
  const params = new URLSearchParams();
  params.set('format', normalized);
  if (options.sectionId) {
    params.set('sectionId', options.sectionId);
  }
  const query = `?${params.toString()}`;
  const accept = normalized === 'csv'
    ? 'text/csv'
    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  const response = await authorizedFetch(`/reports/student/export${query}`, {
    headers: { Accept: accept },
  });
  const blob = await response.blob();
  return {
    blob,
    filename: extractFilename(response, `attendance.${normalized}`),
    contentType: response.headers.get('Content-Type') ?? accept,
  };
}

export async function fetchProfessors(params: { activeOnly?: boolean } = {}) {
  const searchParams = new URLSearchParams();
  if (params.activeOnly) {
    searchParams.set('active', 'true');
  }
  const query = searchParams.toString();
  return request<ProfessorDirectoryEntry[]>(`/professors${query ? `?${query}` : ''}`, 'GET');
}

export async function fetchSectionStudents(sectionId: string) {
  return request<Student[]>(`/sections/${sectionId}/students`, 'GET');
}

export async function scheduleSession(sectionId: string, payload: ScheduleSessionRequest) {
  return request<SessionSummary>(`/sections/${sectionId}/sessions`, 'POST', payload);
}

export async function createSection(payload: CreateSectionRequest & { studentIds?: string[] }) {
  const { studentIds, ...rest } = payload;
  const body = {
    ...rest,
    ...(Array.isArray(studentIds) && studentIds.length > 0 ? { student_ids: studentIds } : {}),
  };
  return request<SectionSummary>('/sections', 'POST', body);
}

export async function importSectionRoster(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request<RosterImportSummary>('/sections/roster-import', 'POST', formData);
}

export async function updateSection(sectionId: string, payload: CreateSectionRequest & { studentIds?: string[] }) {
  const { studentIds, ...rest } = payload;
  const body = {
    ...rest,
    ...(Array.isArray(studentIds) && studentIds.length > 0 ? { student_ids: studentIds } : {}),
  };
  return request<SectionSummary>(`/sections/${sectionId}`, 'PUT', body);
}

export async function deleteSection(sectionId: string) {
  return request<void>(`/sections/${sectionId}`, 'DELETE');
}

export async function fetchSection(sectionId: string) {
  return request<SectionSummary>(`/sections/${sectionId}`, 'GET');
}

export async function fetchSections(params: { courseId?: string; professorId?: string; activeOnly?: boolean } = {}) {
  const searchParams = new URLSearchParams();
  if (params.courseId) {
    searchParams.set('courseId', params.courseId);
  }
  if (params.professorId) {
    searchParams.set('professorId', params.professorId);
  }
  if (params.activeOnly) {
    searchParams.set('active', 'true');
  }
  const query = searchParams.toString();
  return request<SectionSummary[]>(`/sections${query ? `?${query}` : ''}`, 'GET');
}

export async function upsertSectionEnrollment(
  sectionId: string,
  payload: (SectionEnrollmentRequest & { studentIds?: string[] }) | SectionEnrollmentRequest = {},
) {
  const { studentIds, ...rest } = payload as SectionEnrollmentRequest & { studentIds?: string[] };
  const body = {
    ...rest,
    ...(Array.isArray(studentIds) && studentIds.length > 0 ? { student_ids: studentIds } : {}),
  };
  return request<Student[]>(`/sections/${sectionId}/enrollments`, 'POST', body);
}

export async function searchStudents(params: { query?: string; limit?: number } = {}) {
  const searchParams = new URLSearchParams();
  if (params.query) {
    searchParams.set('q', params.query);
  }
  if (typeof params.limit === 'number') {
    searchParams.set('limit', String(params.limit));
  }
  const query = searchParams.toString();
  return request<Student[]>(`/students/search${query ? `?${query}` : ''}`, 'GET');
}

export async function uploadFaceImage(studentId: string, file: File | Blob, options: FaceImageUploadOptions = {}) {
  const formData = new FormData();
  formData.append('file', file);

  if (typeof options.frameWidth === 'number') {
    formData.append('frameWidth', Math.round(options.frameWidth).toString());
  }
  if (typeof options.frameHeight === 'number') {
    formData.append('frameHeight', Math.round(options.frameHeight).toString());
  }
  const bbox = options.boundingBox;
  if (bbox) {
    formData.append('bboxX', Math.round(bbox.x).toString());
    formData.append('bboxY', Math.round(bbox.y).toString());
    formData.append('bboxWidth', Math.round(bbox.width).toString());
    formData.append('bboxHeight', Math.round(bbox.height).toString());
  }

  const searchParams = new URLSearchParams();
  if (options.upsert) {
    searchParams.set('upsert', 'true');
  }

  const path = `/storage/face-images/${studentId}` + (searchParams.size ? `?${searchParams.toString()}` : '');
  return request<FaceImageUploadResponse>(path, 'POST', formData);
}

export async function listFaceImages(studentId: string) {
  return request<FaceImageFile[]>(`/storage/face-images/${studentId}`, 'GET');
}

export async function deleteFaceImage(studentId: string, fileName?: string) {
  const searchParams = new URLSearchParams();
  if (fileName) {
    searchParams.set('fileName', fileName);
  }
  const path = `/storage/face-images/${studentId}` + (searchParams.size ? `?${searchParams.toString()}` : '');
  return request<void>(path, 'DELETE');
}

export function faceImageDownloadUrl(studentId: string, fileName: string) {
  const params = new URLSearchParams({ fileName });
  return buildApiUrl(`/storage/face-images/${studentId}/download?${params.toString()}`);
}

export async function downloadFaceImage(studentId: string, fileName: string) {
  const params = new URLSearchParams({ fileName }).toString();
  const response = await authorizedFetch(`/storage/face-images/${studentId}/download?${params}`);
  return response.blob();
}

export async function downloadCompanionInstaller(platform: 'mac' | 'windows') {
  const response = await authorizedFetch(`/companion/releases/latest/download/${platform}`);
  const blob = await response.blob();
  const fallbackName = platform === 'mac' ? 'companion-installer-mac' : 'companion-installer-windows';
  const filename = extractFilename(response, fallbackName);
  return { blob, filename };
}

export async function analyzeCaptureFrame(imageData: string) {
  return request<FaceCaptureAnalysisResponse>('/face-capture/analyze', 'POST', { imageData });
}

export async function fetchFaceDataStatus(studentId: string) {
  return request<FaceDataStatus>(`/face-data/${studentId}/status`, 'GET');
}

export async function listFaceData(studentId?: string) {
  const search = studentId ? `?studentId=${studentId}` : '';
  return request<FaceData[]>(`/face-data${search}`, 'GET');
}

export async function createFaceData(studentId: string, payload: FaceDataCreateRequest) {
  return request<FaceData>(`/face-data/${studentId}/images`, 'POST', payload);
}

export async function deleteFaceData(payload: { ids?: string[]; studentId?: string }) {
  const searchParams = new URLSearchParams();
  if (payload.studentId) {
    searchParams.set('studentId', payload.studentId);
  }

  const body: FaceDataDeleteRequest = payload.ids ? { ids: payload.ids } : {};
  const query = searchParams.size ? `?${searchParams.toString()}` : '';
  return request<void>(`/face-data${query}`, 'DELETE', Object.keys(body).length ? body : undefined);
}

export async function issueCompanionAccessToken(sectionId: string) {
  return request<CompanionAccessTokenResponse>('/companion/access-token', 'POST', { sectionId });
}

function sanitizeDownloadFilename(value: string): string {
  if (!value) {
    return value;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return trimmed;
  }

  const rfcMatch = trimmed.match(/^=\?([^?]+)\?([bBqQ])\?([^?]+)\?=$/);
  if (rfcMatch) {
    const [, charset = 'utf-8', encoding = 'Q', encodedText = ''] = rfcMatch;
    if (encoding.toUpperCase() === 'B') {
      try {
        const decoded = atob(encodedText);
        const bytes = Uint8Array.from(decoded, (char) => char.charCodeAt(0));
        return new TextDecoder(charset).decode(bytes);
      } catch (error) {
        console.warn('Failed to decode base64 filename', error);
        return trimmed.replace(/\r|\n/g, '');
      }
    }
    return decodeQuotedPrintableWord(encodedText, charset);
  }

  if (/^=_UTF-8_Q_/i.test(trimmed) && /_=$/.test(trimmed)) {
    const inner = trimmed.slice('=_UTF-8_Q_'.length, trimmed.length - 2);
    return decodeQuotedPrintableWord(inner, 'utf-8');
  }

  return trimmed.replace(/\r|\n/g, '');
}

function decodeQuotedPrintableWord(value: string, charset: string): string {
  try {
    const bytes: number[] = [];
    for (let i = 0; i < value.length; i++) {
      const char = value[i];
      if (char === '_') {
        bytes.push(0x20);
      } else if (char === '=' && i + 2 < value.length) {
        const hex = value.substring(i + 1, i + 3);
        const parsed = Number.parseInt(hex, 16);
        if (!Number.isNaN(parsed)) {
          bytes.push(parsed);
          i += 2;
        } else {
          bytes.push(char.charCodeAt(0));
        }
      } else {
        bytes.push(char.charCodeAt(0));
      }
    }
    return new TextDecoder(charset).decode(new Uint8Array(bytes));
  } catch (error) {
    console.warn('Failed to decode quoted-printable filename', error);
    return value.replace(/\r|\n/g, '').replace(/_/g, ' ');
  }
}

export default {
  fetchApplicationSettings,
  validateStaffPasscode,
  validateAdminPasscode,
  fetchCurrentUser,
  signIn,
  signOut,
  signUp,
  requestPasswordReset,
  confirmPasswordReset,
  manageSession,
  recognizeFace,
  startTraining,
  subscribeToSessionEvents,
  patchAttendanceRecord,
  upsertAttendanceRecord,
  fetchSession,
  fetchSessionAttendance,
  fetchSessionAttendanceStats,
  fetchSessionStudents,
  fetchAttendance,
  fetchProfessorDashboard,
  fetchStudentDashboard,
  fetchCourses,
  createCourse,
  updateCourse,
  deleteCourse,
  fetchAdminCourses,
  fetchAdminCourseSections,
  fetchAdminCourseStudents,
  fetchAdminSections,
  createAdminSection,
  fetchAdminUsers,
  updateAdminUser,
  deleteAdminUser,
  fetchProfessorCourses,
  fetchProfessorSessions,
  fetchProfessorSections,
  getCompanionRelease,
  fetchProfessorStudentReports,
  fetchAdminStudentReports,
  fetchProfessorSectionReports,
  fetchAdminSectionReports,
  fetchProfessors,
  fetchSectionSessions,
  fetchSectionReportDetail,
  fetchAdminSectionReportDetail,
  fetchSectionStudents,
  fetchSectionAnalytics,
  fetchStudentReportDetail,
  fetchAdminStudentReportDetail,
  fetchStudentSections,
  fetchStudentSectionDetail,
  createSection,
  updateSection,
  deleteSection,
  fetchSection,
  fetchSections,
  upsertSectionEnrollment,
  scheduleSession,
  downloadSectionReport,
  downloadAdminSectionReport,
  downloadStudentReport,
  downloadAdminStudentReport,
  downloadMyAttendanceReport,
  uploadFaceImage,
  listFaceImages,
  deleteFaceImage,
  faceImageDownloadUrl,
  downloadFaceImage,
  analyzeCaptureFrame,
  fetchFaceDataStatus,
  listFaceData,
  createFaceData,
  deleteFaceData,
  issueCompanionAccessToken,
  downloadCompanionInstaller,
};
