# SmartAttendance Platform

SmartAttendance is a two-part attendance solution: a React web app that students and staff use in their browsers, and a Spring Boot service that handles authentication, face-recognition workflows, and reporting APIs. The web client only talks to the backend over HTTPS, so each tier can be hosted independently.

Head into `backend/` or `frontend/` for developer setup guides and environment details.
This repo uses a simple two-branch workflow:


| Branch      | Purpose               | Typical Usage                               |
|-------------|-----------------------|---------------------------------------------|
| `main`      | Local development     | Windows-focused workflows and tooling.      |
| `deploy`    | Cloud deployment      | macOS/Linux development and cloud releases. |

> Note: The `deploy` branch is the source of truth for production/cloud deployments.
> The `main` branch is intended for Windows-specific local development, while macOS users should work from `deploy` for full compatibility.


This README describes the backend implementation of the **Smart Attendance System** submission, with a focus on how the codebase satisfies the module’s project requirements and rubrics (code quality, application behaviour, and production readiness). It is written for the **instructors and graders** reviewing the project.

The backend is a **Java 21 / Spring Boot 3** service that exposes a REST API and server‑sent events (SSE) to the frontend and to a native **companion** process that runs OpenCV‑based face detection and recognition.

---

## 1. High‑Level Overview

### 1.1 Technology Stack

- **Language & Framework**
  - Java 21
  - Spring Boot 3 (REST controllers, configuration, security, dependency injection)
- **Data & Persistence**
  - Supabase Postgres (relational data: students, sections, sessions, attendance, face metadata)
  - JPA entities and Spring Data / JDBC repositories in `com.smartattendance.supabase.entity` and `com.smartattendance.supabase.repository`
- **Computer Vision**
  - OpenCV via JavaCV
  - Haar cascades for face detection (`com.smartattendance.vision.HaarFaceDetector`)
  - LBPH face recognizer (`com.smartattendance.vision.recognizer.LBPHRecognizer`)
  - Pre‑processing pipeline under `com.smartattendance.vision.preprocess`
  - Liveness detection (`com.smartattendance.vision.LivenessDetector`)
- **Messaging**
  - Server‑Sent Events for live session updates (`SessionEventPublisher`, `SessionEventController`)
- **Storage**
  - Supabase Storage buckets for face captures and trained model artifacts
  - Local filesystem workspace under `backend/service/runtime/` (e.g. `config.properties`, `data/`, models)
- **Configuration**
  - Externalised via `application.properties`, `application-*.yml`, `.env.local`, and `runtime/config.properties`
- **Security**
  - Supabase Auth + JWT (`SupabaseAuthController`, `SecurityConfiguration`)
  - Role‑based access control (`Role`, `@PreAuthorize` annotations)
  - Request rate limiting (`RequestRateLimiter`)

---

## 2. Architectural Structure & OOP Design

The backend follows a **layered architecture** and emphasises modular, object‑oriented design:

- **Entry Point**
  - `AttendanceApplication` – Spring Boot main class.
- **Configuration Layer**
  - `config.AttendanceProperties` – watches and reloads `runtime/config.properties` at runtime.
  - `config.SecurityConfiguration`, `config.VisionConfiguration`, `config.PasswordEncoderConfiguration`.
- **Domain Model & Persistence**
  - Entities: `AttendanceRecordEntity`, `AttendanceSessionEntity`, `CourseEntity`, `SectionEntity`, `ProfileEntity`, `FaceDataEntity`, `StudentEnrollmentEntity`.
  - Repositories: `AttendanceRecordRepository`, `AttendanceSessionRepository`, `CourseRepository`, `SectionRepository`, `StudentEnrollmentRepository`, `FaceDataRepository`, plus JDBC‑based repositories for reporting and admin.
- **Service Layer**
  - Attendance & sessions: `AttendanceService`, `SessionLifecycleService`, `SessionQueryService`, `SessionEventPublisher`.
  - Teaching & roster: `TeachingManagementService`, `DashboardService`.
  - Face data & recognition: `FaceCaptureAnalysisService`, `FaceImageStorageService`, `FaceDataAdminService`, `SectionModelService`.
  - Profiles & auth: `ProfileService`, `SupabaseAuthService` (via `supabase.service.*`).
  - Reporting: `ReportingService`, `TeachingManagementService`.
- **Web / API Layer**
  - Controllers in `com.smartattendance.supabase.web`:
    - `AttendanceController` (`/api/attendance`)
    - `SessionLifecycleController` & `SessionEventController` (`/api/sessions`)
    - `TeachingController` (`/api` – sections, roster, teaching views)
    - `FaceCaptureController` (`/api/face-capture`)
    - `FaceDataAdminController` (`/api/face-data`)
    - `FaceImageStorageController` (`/api/storage/face-images`)
    - `DashboardController` (`/api/dashboard`)
    - `ReportingController` (`/api/reports`)
    - `SupabaseAuthController` (`/api/auth`)
    - `ApplicationSettingsController`, `ProfileController`, plus admin/support sub‑controllers.
- **Vision Subsystem**
  - Interface `Recognizer` with concrete implementation `LBPHRecognizer` (Strategy pattern for recognition algorithms).
  - Strategy interfaces for preprocessing: `Preprocessor`, with concrete classes `GrayscaleProcessor`, `Normalizer`, `ClaheProcessor`, etc.
  - `HaarFaceDetector` – encapsulates OpenCV cascade loading and face detection.
  - `ModelManager` – manages model training, caching and asynchronous updates using `ExecutorService` and `CompletableFuture`.
  - `LivenessDetector` – blink‑based and texture‑based liveness detection.
  - `FaceTrack`, `FaceTrackGroup` – multi‑face tracking across frames.

Overall, each class has a clear single responsibility, and packages are organised by feature, matching the rubric’s expectations for modularisation and design clarity.

---

## 3. Mapping to Project Requirements

This section explicitly links the course project requirements to concrete backend implementation details.

### 3.1 Student Enrollment & Management

**Requirement:** Manage students (ID, name, class/group, contact) and enrol them with face data; validate images before storing.

**Backend implementation:**

- **Student data model & enrolment**
  - `ProfileEntity` + `ProfileRepository` – core student profile (name, email, avatar).
  - `StudentEnrollmentEntity` / `StudentEnrollmentRepository` – links students to sections (classes / groups).
  - DTOs like `StudentDto`, `StudentSectionDetailDto`, `StudentAttendanceHistoryDto` carry profile + section details to the frontend.
  - `TeachingManagementService` orchestrates creation of courses, sections and enrolments; it exposes roster views via `TeachingController`.
- **Image capture & validation**
  - `FaceCaptureController` (`/api/face-capture`) + `FaceCaptureAnalysisService`:
    - Accepts base64‑encoded image data from the frontend.
    - Decodes into an OpenCV `Mat` (`Imgcodecs.imdecode`).
    - Runs **face detection** via `HaarFaceDetector` to ensure a face is present.
    - Evaluates **image quality** via `ImageQuality.laplacianVariance` (sharpness) and brightness estimation.
    - Returns a structured `FaceCaptureAnalysisResponse` with quality metrics; low‑quality or no‑face/more than one face in images are rejected.
- **Face data persistence**
  - `FaceImageStorageService`:
    - Stores face captures in Supabase Storage buckets with systematic naming (student ID / section IDs), and records metadata such as timestamps and URLs.
  - `FaceDataEntity` + `FaceDataRepository`:
    - Persists the link between a student and their captured face images / processed embeddings.
  - `FaceDataAdminService` & `FaceDataAdminController`:
    - Allow administrators/professors to manage and review face data for a section.

This collectively fulfils the student enrolment requirement with strong input validation and persistent storage.

---

### 3.2 Attendance Session Management

**Requirement:** Create and manage attendance sessions with course, date/time, location, and roster of expected students. Support open/close lifecycle and prevent deletion of active sessions.

**Backend implementation:**

- **Session model**
  - `AttendanceSessionEntity` – stores session metadata (course/section, scheduled start/end, location, status).
  - `AttendanceRecordEntity` – per‑student record with fields:
    - `Status` enum (`pending`, `present`, `absent`, `late`)
    - `MarkingMethod` enum (`auto`, `manual`)
    - Timestamps (`markedAt`, `enrolledAt`, etc.).
- **Lifecycle management**
  - `SessionLifecycleService`:
    - Creates new sessions/resumes existing sessions, validates scheduling, and associates them to a section roster.
    - Opens/closes sessions, enforcing business rules based on current state and time.
    - On close, finalises pending records (e.g., marking remaining students as **Absent**).
  - `SessionLifecycleController` (`/api/sessions`):
    - Exposes REST endpoints to create, open, close, and update sessions.
  - `SessionQueryService` + `SessionQueryController`:
    - Provide queries for sessions, summaries and analytics (including late rates and status breakdowns)(bonus).
- **Roster management**
  - `TeachingManagementService` + `TeachingController`:
    - Maintain the enrolment roster for each section.
    - `buildRoster` and related helpers construct the expected student list from `StudentEnrollmentEntity`.
    - Roster and attendance history are exposed via DTOs (`SectionSummaryDto`, `SessionSummaryDto`, `StudentSectionDetailDto`).

---

### 3.3 Face Detection & Recognition

**Requirement:** Capture frames, detect faces (Haar or other), preprocess (grayscale, normalization, resize), recognise against enrolled students, and display confidence scores.

**Backend implementation:**

- **Face detection**
  - `HaarFaceDetector`:
    - Loads a Haar cascade from classpath resources (`/vision/models/...`).
    - Uses OpenCV’s `CascadeClassifier` and configurable parameters from `config.properties`:
      - `detect.min_face`
      - `detect.cascade.scale_factor`
      - `detect.cascade.min_neighbors`
    - Returns bounding boxes (`Rectangle`) for detected faces.
    - Used by both `FaceCaptureAnalysisService` (capture‑time validation) and recognition pipeline.
- **Pre‑processing pipeline (Strategy pattern)**
  - `Preprocessor` (functional interface) – strategy for image pre‑processing.
  - Concrete strategies:
    - `GrayscaleProcessor` – converts to grayscale.
    - `Normalizer` – normalises intensity.
    - `ClaheProcessor` – contrast‑limited adaptive histogram equalisation.
    - `Augmenter` – optional augmentation steps.
  - `FaceImageProcessor`:
    - Composes a pipeline of `Preprocessor` strategies and applies them to each detected face.
    - Handles cropping, resizing and encoding/decoding (via `MatOfByte`, `Imgcodecs`).
- **Recognition interface & LBPH implementation**
  - `Recognizer` interface:
    - `train(Path root)` – train on a directory of labelled face images.
    - `predict(Mat face)` – returns a prediction with label and confidence.
  - `LBPHRecognizer`:
    - Wraps OpenCV’s `LBPHFaceRecognizer`.
    - Reads/writes trained model files and label mappings.
    - Uses LBPH parameters configured in `runtime/config.properties`:
      - `recognition.lbph.radius`
      - `recognition.lbph.neighbors`
      - `recognition.lbph.grid_x`, `recognition.lbph.grid_y`.
- **Model management & performance**
  - `ModelManager`:
    - Maintains cached in‑memory recognizer models per section, uploads model artifacts to supabase for companion app to use.
    - Uses `ExecutorService` and `CompletableFuture` for **async training and updates** (`ensureLoadedAsync`, `retrainAllAsync`, `updateStudentAsync`, `removeStudentAsync`), so HTTP threads remain responsive.
    - Manages underlying on‑disk model files in `runtime/data/model`.
  - `SectionModelService`:
    - Orchestrates training and refresh of section‑specific recognition models based on stored face data.
- **Liveness detection (Bonus)**
  - `LivenessDetector`:
    - Implements a simple blink‑cycle plus texture check:
      - Uses eye region detection (another cascade) and Laplacian variance (`ImageQuality.laplacianVariance`) to detect sharpness.
      - Distinguishes real faces from flat or spoofed images.
    - Available to the companion / live recognition flows as an optional anti‑spoofing measure.

---

### 3.4 Marking Attendance (Automatic & Manual)

**Requirement:** Automatically mark Present/Late based on recognition results and timestamps, support manual overrides, avoid duplicates, and handle edge cases.

**Backend implementation:**

- **Automatic marking logic**
  - `AttendanceRecordEntity`:
    - Encodes status (`pending`, `present`, `absent`, `late`) and marking method (`auto`, `manual`).
  - `AttendanceService`:
    - Provides methods to **upsert** and **update** attendance records, invoked by `AttendanceController`.
    - Normalises status input (e.g. strings from clients) to the enum using helper `parseStatus` / `normaliseStatuses`.
    - Exposes a search API with specifications (filters by session, section, status, date range).
  - `AttendanceController` (`/api/attendance`):
    - `POST /api/attendance` – upsert logic via `AttendanceUpsertRequest`, used by recognition flows for automatic marking.
    - `PATCH /api/attendance/{id}` – partial update of existing records.
- **Session‑driven updates & SSE**
  - `SessionEventPublisher`:
    - Manages SSE connections (`SseEmitter`) per session and pushes updates when records change.
  - `SessionEventController`:
    - Exposes `/api/sessions/{id}/events` for live UI updates of roster status and counters.
- **Manual overrides**
  - The same `AttendanceController` endpoints support manual changes:
    - The frontend can submit `AttendanceUpsertRequest` to override auto‑marked records.
    - `MarkingMethod` is set appropriately (`manual` vs `auto`) and preserved for auditability.
- **Late / absent handling**
  - `SessionLifecycleService` and reporting services:
    - Compute late rates and status statistics (`SessionSummaryDto`, `SectionAnalyticsDto`).
    - On session close, any remaining `pending` students can be converted to `absent`.
  - `SessionQueryService`:
    - Aggregates status counts (including late) and average late rate to power dashboards and reports.

While the names `AttendanceMarker`, `AutoMarker`, `ManualMarker` are not used explicitly, the responsibilities described in the project brief are handled by the combination of `AttendanceService`, `AttendanceController`, `SessionLifecycleService`, and `SessionEventPublisher`.

---

### 3.5 Reporting & Export

**Requirement:** Summaries, export to CSV/XLSX/PDF; include per‑student records with status, timestamp, confidence, method, notes.

**Backend implementation:**

- **Reporting service**
  - `ReportingService`:
    - Generates **per‑section** and **per‑student** attendance exports.
    - Uses repository projections (`ReportingJdbcRepository`) to fetch denormalised rows.
    - Builds:
      - CSV via `buildSectionCsv` / `buildStudentCsv`
      - XLSX via `buildSectionWorkbook` / `buildStudentWorkbook`
    - `ReportFormat` enum supports `CSV` and `XLSX` (Excel) export types.
- **Export wrapper**
  - `ReportExport`:
    - Encapsulates exported content bytes, `contentType` (`text/csv` or `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`), and `fileName`.
- **API layer**
  - `ReportingController` (`/api/reports`):
    - Endpoints to download reports for sections and individual students.
- **Data included**
  - CSV builder includes:
    - Student ID / number, name, email.
    - Session date, start time, end time, location.
    - Attendance `status` and `markingMethod` (auto/manual).
    - `markedAt` timestamp.
    - Notes / comments.
  - Confidence scores from recognizer are logged and can be surfaced in logs and analytics; the export focuses on final marked outcome for clarity.

---

### 3.6 GUI & Usability – Backend Support

The project specification allows either a desktop GUI or web application. The backend is designed to support a **web application** with the following usability‑oriented features:

- **RESTful, resource‑oriented endpoints** with clear prefixes:
  - `/api/attendance`, `/api/sessions`, `/api/face-capture`, `/api/face-data`, `/api/storage/face-images`, `/api/reports`, `/api/dashboard`, `/api/auth`, `/api/settings`, `/api/profile`, etc.
- **Server‑Sent Events (SSE)**:
  - `SessionEventPublisher` + `SessionEventController` provide low‑latency, push‑based updates to the UI for live session status.
- **Input validation**
  - Controllers use `@Validated` and `@Valid` (e.g. `FaceCaptureController`) and DTOs with Jakarta Bean Validation annotations.
- **Error handling**
  - Services throw `ResponseStatusException` for business rule violations.
  - Auth errors are handled via `SupabaseAuthExceptionHandler`.
- **Role‑aware views**
  - `TeachingController`, `DashboardController`, `ReportingController` and others use `@PreAuthorize` to enforce roles (`ADMIN`, `PROFESSOR`, `STUDENT`), ensuring each UI persona only sees what they should.

These backend choices enable a frontend that aligns with Nielsen’s usability heuristics (clear visibility of system status, match between system and real‑world concepts, feedback, etc.).

---

### 3.7 Configuration & Logging

**Requirement:** Externalise configuration and implement logging.

**Backend implementation:**

- **Externalised configuration**
  - `application.properties`:
    - Uses environment variables with sensible defaults:
      - Database (`SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`, etc.)
      - Supabase auth and storage (`SUPABASE_PROJECT_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_JWT_SECRET`, storage bucket names).
      - Data directory: `attendance.data-dir=${DATA_DIR:backend/runtime/data}`.
  - `application-dev.yml`, `application-prod.yml` for profile‑specific overrides.
  - `.env.local` is imported via `spring.config.import=optional:file:../../.env.local[.properties]...`.
  - `runtime/config.properties`:
    - Human‑editable runtime options, including:
      - `faces.dir`, `model.dir`
      - `camera.index`, `camera.fps`
      - Detection thresholds (`detect.min_face`, `detect.cascade.scale_factor`, `detect.cascade.min_neighbors`)
      - Capture quality thresholds (`capture.blur.variance_threshold`, `capture.post_blur.variance_threshold`)
      - LBPH parameters (`recognition.lbph.*`)
- **Runtime config watcher**
  - `AttendanceProperties`:
    - Loads `config.properties` on startup.
    - Uses `WatchService` to watch the file/directory and notifies listeners when values change.
    - Provides a central, strongly‑typed configuration object that other components can subscribe to.
- **Logging**
  - Classes like `FaceCaptureAnalysisService`, `FaceImageStorageService`, `SessionLifecycleService`, `SessionEventPublisher`, `SectionModelService` use SLF4J loggers (`LoggerFactory.getLogger(...)`).
  - Logging is used to:
    - Record OpenCV loading failures and fallbacks.
    - Log face capture analysis outcomes and quality issues.
    - Trace model training and cache loading.
    - Track session lifecycle events and SSE connection counts.
  - Spring Boot’s default Logback configuration is used, and can be customised further if required.

---

### 3.8 Non‑Functional Requirements

- **Performance**
  - Recognition training and model loading are offloaded to a dedicated `ExecutorService` (`ModelManager`), preventing long‑running tasks from blocking HTTP request threads.
  - Haar cascades and LBPH are lightweight and optimised for real‑time detection/recognition scenarios.
  - SSE avoids inefficient polling for live session status.
- **Accuracy**
  - Pre‑processing pipeline (grayscale, histogram equalisation, normalisation) plus tunable LBPH parameters and quality thresholds support robust recognition in varied lighting.
  - Liveness checks reduce spoofing risk and help avoid false positives.
- **Security**
  - Integration with Supabase Auth:
    - JWT verification and cookie handling (via auth services and `SecurityConfiguration`).
    - Role‑based access control (`Role` enum and `@PreAuthorize`).
  - Instructor/admin passcodes (`AdminPasscodeService`, `ProfessorPasscodeService`) to protect critical operations when needed.
  - Request rate limiting (`RequestRateLimiter`) to guard against brute force / abuse.
- **Maintainability & Extensibility**
  - Clear separation of concerns:
    - Controllers ↔ Services ↔ Repositories ↔ Entities.
    - Dedicated packages for `attendance`, `session`, `face`, `recognition`, `reporting`, `dashboard`, `auth`, `system`.
  - Low coupling, high cohesion: each service targets a specific domain concern.
  - Strategy interfaces (`Recognizer`, `Preprocessor`) allow for future algorithms (e.g., deep‑learning‑based recognizers) without impacting higher‑level code.

---

## 4. Feature & Bonus Matrix

The table below summarises core and extended features implemented in the backend.

| Area                          | Feature / Functionality                                                                 | Key Classes / Endpoints                                                                                                          | Status    |
|-------------------------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|-----------|
| Student Management            | Student profile and enrolment per section                                               | `ProfileEntity`, `StudentEnrollmentEntity`, `TeachingManagementService`, `TeachingController`                                   | Core      |
| Student Face Capture          | Capture, validate, and analyse face images                                              | `FaceCaptureController` (`/api/face-capture`), `FaceCaptureAnalysisService`, `HaarFaceDetector`, `ImageQuality`                 | Core      |
| Face Data Storage             | Persist and manage face images / metadata                                               | `FaceImageStorageService`, `FaceDataEntity`, `FaceDataRepository`, `FaceDataAdminController`                                    | Core      |
| Session Creation              | Create and schedule attendance sessions per section                                     | `AttendanceSessionEntity`, `SessionLifecycleService`, `TeachingManagementService`, `SessionLifecycleController`                | Core      |
| Live Session Management       | Open/close sessions, update roster status                                               | `SessionLifecycleService`, `SessionEventPublisher`, `SessionEventController`, `SessionQueryService`                             | Core      |
| Automatic Attendance          | Auto‑mark `present` / `late` based on recognition and timestamps                        | `AttendanceService`, `AttendanceController`, `AttendanceRecordEntity`, `SectionModelService`, `ModelManager`                    | Core      |
| Manual Overrides              | Allow instructor to override auto‑marked attendance                                     | `AttendanceController` (`POST`/`PATCH`), `AttendanceService`                                                                    | Core      |
| Multi‑face Handling           | Tracking multiple faces across frames                                                   | `FaceTrack`, `FaceTrackGroup`                                                                                                   | Bonus     |
| Liveness Detection            | Blink + texture‑based liveness check                                                    | `LivenessDetector`                                                                                                              | Bonus     |
| Analytics Dashboard           | Summary stats for professors and students (rates, counts, trends)                       | `DashboardService`, `DashboardController`, `ProfessorDashboardSummary`, `StudentDashboardSummary`                               | Bonus     |
| Reporting & Export            | Export attendance (section / student) as CSV/XLSX                                       | `ReportingService`, `ReportExport`, `ReportingController` (`/api/reports`)                                                      | Core/Plus |
| Cloud Storage Integration     | Store face images and model artifacts in Supabase buckets                               | `FaceImageStorageService`, Supabase config in `application.properties`                                                          | Bonus     |
| Externalised Configuration    | Environment + file‑based configuration for DB, queues, camera, detection, recognizer    | `application.properties`, `.env.local`, `runtime/config.properties`, `AttendanceProperties`                                     | Core      |
| Auth & Authorisation          | JWT‑based auth, role‑based access control, passcode‑protected flows                     | `SecurityConfiguration`, `SupabaseAuthController`, `AdminPasscodeService`, `ProfessorPasscodeService`, `Role`, `RequestRateLimiter` | Core/Plus |
| Companion Integration         | Companion app bootstrapping and asset delivery                                          | `companion` module (`CompanionApplication`, `ModelDownloader`, `SessionRuntime`), `CompanionAssetController`, `CompanionReleaseController` | Bonus     |
| SSE for Live Updates          | Server‑sent events for real‑time session status                                         | `SessionEventPublisher`, `SessionEventController`                                                                               | Bonus     |

---

## 5. Companion Module

The `backend/companion` module is a separate Java application designed to run on the instructor’s machine alongside the browser:

- `CompanionApplication` – entry point for the companion.
- `CompanionHttpServer` – lightweight HTTP server that exposes a local API for the frontend (via the backend).
- `LiveRecognitionRuntime` & related classes in `companion.recognition` – handle live camera frames, integrate with `HaarFaceDetector` and the trained LBPH models downloaded from the backend (`ModelDownloader`).
- `SessionRuntime` / `CompanionSessionManager` – synchronise local recognition state with backend sessions, publishing recognition events back to the backend via `BackendEventForwarder`.

From the backend perspective, the companion is treated as a **trusted client specialised for video processing**, while the backend remains the single source of truth for enrolments, sessions, and attendance records.

---

## 6. How to Run the Backend (for Reviewers)

Head into `backend/` or `frontend/` for developer setup guides and environment details.

---
## 7. Conclusion

The backend is implemented using established Spring Boot engineering practices—layered architecture, DTO-driven APIs, repository-based persistence, asynchronous processing, externalised configuration, and robust security—which collectively satisfy the module’s core Smart Attendance System requirements:

- Student enrolment with validated face captures  
- Session creation, real-time session management, and reliable attendance marking  
- OpenCV-powered face detection, recognition, and liveness checks  
- Durable data storage, analytics dashboards, and exportable attendance reports  

Taken together, these design choices make the system not just a course project, but a production-oriented backend that could realistically be operated in an institutional environment with only minimal additional work around scaling, observability, and infrastructure hardening.
