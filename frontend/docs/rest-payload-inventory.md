# REST Payload Interface Inventory

This catalog tracks the TypeScript interfaces that represent REST or SSE payloads in the
frontend codebase and the corresponding Java DTOs served by the backend. The goal is to
ensure every payload exposed by the API has a strongly typed contract on both sides so the
OpenAPI generator can become the single source of truth.

| TypeScript interface | Source file | Purpose | Java DTO counterpart |
| --- | --- | --- | --- |
| `Student` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Session roster entries returned from `/api/sessions/{id}/students` and `/api/sections/{id}/students`. | `com.smartattendance.supabase.dto.StudentDto` |
| `SessionAttendanceRecord` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Attendance records fetched from `/api/sessions/{id}/attendance`. | `com.smartattendance.supabase.dto.SessionAttendanceRecord` |
| `SessionDetails` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Session metadata from `/api/sessions/{id}`. | `com.smartattendance.supabase.dto.SessionDetailsDto` |
| `RecognitionLogEntry` | `src/components/live-session/LiveSessionDashboard.tsx` | Recognition history fetched via `/api/sessions/{id}/recognition-log`. | `com.smartattendance.supabase.dto.RecognitionLogEntryDto` |
| `SessionActionEvent` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Server-sent events broadcast on `session-action`. | `com.smartattendance.supabase.dto.events.SessionActionEvent` |
| `RecognitionEvent` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Server-sent events broadcast on `recognition`. | `com.smartattendance.supabase.dto.events.RecognitionEvent` |
| `AttendanceEvent` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Server-sent events broadcast on `attendance`. | `com.smartattendance.supabase.dto.events.AttendanceEvent` |
| `PagedResponse<T>` | `src/lib/api.ts` | Wrapper for paginated list endpoints such as `/api/attendance`. | `com.smartattendance.supabase.dto.PagedResponse` |
| `ApplicationSettingsResponse` | `src/lib/api.ts` | Passcode configuration (professor/admin) returned from `/api/settings`. | `com.smartattendance.supabase.dto.ApplicationSettingsResponse` (new) |
| `PasscodeValidationResponse` | `src/lib/api.ts` | Passcode validation outcome from `/api/settings/validate-staff-passcode` and `/api/settings/validate-admin-passcode`. | `com.smartattendance.supabase.dto.PasscodeValidationResponse` (new) |
| `AuthUserDto` | `src/lib/api.ts` | Authenticated Supabase user payload nested under sign-in responses. | `com.smartattendance.supabase.dto.auth.SupabaseUserDto` |
| `AuthSessionDto` | `src/lib/api.ts` | Session metadata produced by `/api/auth/sign-in`. | `com.smartattendance.supabase.dto.auth.AuthSessionResponse.SessionDto` |
| `SignInResponse` | `src/lib/api.ts` | Response body for `/api/auth/sign-in`. | `com.smartattendance.supabase.dto.auth.AuthSessionResponse` |
| `RecognitionResult` | `src/lib/api.ts` | Recognition outcome returned from `/api/recognition`. | `com.smartattendance.supabase.dto.RecognitionResponse` |
| `ProfessorDirectoryEntry` | `src/lib/api.ts` | Lightweight professor listing for directory views. | `com.smartattendance.supabase.dto.ProfessorDirectoryEntry` (new) |
| `ScheduleSessionPayload` | `src/lib/api.ts` | Request body for `/api/sections/{id}/sessions`. | `com.smartattendance.supabase.dto.ScheduleSessionRequest` |
| `SectionSummary` | `src/lib/api.ts` | Section listing responses from `/api/sections` and related endpoints. | `com.smartattendance.supabase.dto.SectionSummaryDto` |
| `AdminCourseSummary` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Administrative course overview from `/api/admin/courses`. | `com.smartattendance.supabase.dto.admin.AdminCourseSummaryDto` (new) |
| `AdminCourseSection` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Course section analytics from `/api/admin/courses/{id}/sections`. | `com.smartattendance.supabase.dto.admin.AdminCourseSectionDto` (new) |
| `AdminCourseStudent` (generated) | `src/lib/generated/openapi-types.ts` (consumed via `src/lib/api.ts`) | Course roster metrics from `/api/admin/courses/{id}/students`. | `com.smartattendance.supabase.dto.admin.AdminCourseStudentDto` (new) |
| `SectionEnrollmentPayload` | `src/lib/api.ts` | Request body for `/api/sections/{id}/enrollments`. | `com.smartattendance.supabase.dto.SectionEnrollmentRequest` |
| `FaceImageUploadResponse` | `src/lib/api.ts` | Upload metadata returned from `/api/storage/face-images/{id}`. | `com.smartattendance.supabase.dto.FaceImageUploadResponse` |
| `FaceImageFile` | `src/lib/api.ts` | Stored file metadata returned from `/api/storage/face-images/{id}`. | `com.smartattendance.supabase.dto.FaceImageFileDto` |
| `FaceDataDto` | `src/lib/api.ts` | Face data records returned from `/api/face-data`. | `com.smartattendance.supabase.dto.FaceDataDto` |

Interfaces such as `StudentViewModel`, `AttendanceRecordViewModel`, and `SessionViewModel`
inside `LiveSessionDashboard.tsx` are local view models derived from the generated API
responses and do not require backend equivalents.
