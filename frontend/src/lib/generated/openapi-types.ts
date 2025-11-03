export interface paths {
    "/api/attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search attendance records
         * @description Retrieves paginated attendance records filtered by session, section, student or date range.
         */
        get: operations["search"];
        put?: never;
        /**
         * Upsert attendance record
         * @description Creates or updates an attendance record based on the supplied identifiers.
         */
        post: operations["upsert"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/attendance/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Update attendance record
         * @description Applies partial updates to an existing attendance record.
         */
        patch: operations["update"];
        trace?: never;
    };
    "/api/auth/password-reset": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Request password reset
         * @description Requests a Supabase password recovery email for the supplied account.
         */
        post: operations["requestPasswordReset"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/password-reset/confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Confirm password reset
         * @description Updates a user's password using a Supabase recovery access token.
         */
        post: operations["confirmPasswordReset"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/sign-in": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Sign in
         * @description Authenticates a user with email and password via Supabase.
         */
        post: operations["signIn"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/sign-out": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Sign out
         * @description Clears Supabase session cookies and revokes refresh tokens when possible.
         */
        post: operations["signOut"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/sign-up": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Sign up
         * @description Creates a new Supabase user account after validating registration policies.
         */
        post: operations["signUp"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/courses": {
        parameters: {
            query?: {
                /** @description Optional search string applied to course code, title, or description */
                q?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List admin courses
         * @description Returns all courses with aggregated enrollment metrics for administrators.
         */
        get: operations["listAdminCourses"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/courses/{courseId}/sections": {
        parameters: {
            query?: {
                /** @description Filter sections by code or professor name */
                q?: string;
            };
            header?: never;
            path?: {
                courseId: string;
            };
            cookie?: never;
        };
        /**
         * List admin course sections
         * @description Returns sections for a course including professor assignments and attendance summaries.
         */
        get: operations["listAdminCourseSections"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/courses/{courseId}/students": {
        parameters: {
            query?: {
                /** @description Filter students by name, email, or student number */
                q?: string;
            };
            header?: never;
            path?: {
                courseId: string;
            };
            cookie?: never;
        };
        /**
         * List admin course students
         * @description Returns students enrolled in the course with attendance metrics.
         */
        get: operations["listAdminCourseStudents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/sections": {
        parameters: {
            query?: {
                /** @description Optional search string applied to course, section, or professor fields */
                q?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List admin sections
         * @description Returns all sections with professor assignments and enrollment metrics for administrators.
         */
        get: operations["listAdminSections"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/sections/{sectionId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: {
                sectionId: string;
            };
            cookie?: never;
        };
        /**
         * Get admin section
         * @description Loads a single section summary including professor assignment.
         */
        get: operations["getAdminSection"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
        /**
         * Update admin section
         * @description Updates section schedule, capacity, and professor assignment.
         */
        put: operations["updateAdminSection"];
    };
    "/api/courses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List courses
         * @description Lists all active courses available for scheduling.
         */
        get: operations["listCourses"];
        put?: never;
        /**
         * Create course
         * @description Creates a new course that can host sections.
         */
        post: operations["createCourse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/courses/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Update course
         * @description Updates course details for the given identifier.
         */
        put: operations["updateCourse"];
        post?: never;
        /**
         * Archive course
         * @description Soft deletes a course so it is no longer selectable.
         */
        delete: operations["deleteCourse"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/dashboard/professor": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Professor dashboard
         * @description Provides aggregate counts for the authenticated professor.
         */
        get: operations["professor"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/dashboard/student": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Student dashboard
         * @description Provides attendance insights for the authenticated student.
         */
        get: operations["student"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/face-data": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List face data
         * @description Returns stored face data records filtered by student when provided.
         */
        get: operations["listFaceData"];
        put?: never;
        post?: never;
        /**
         * Delete face data
         * @description Removes stored face data for one or more students.
         */
        delete: operations["deleteFaceData"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/face-data/{studentId}/images": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Create face data
         * @description Registers captured face data for a student and returns the persisted record.
         */
        post: operations["createFaceData"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/face-data/{studentId}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get face data status
         * @description Provides a summary of biometric data availability for a student.
         */
        get: operations["status"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Current user
         * @description Returns the authenticated user's profile and granted roles.
         */
        get: operations["currentUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/professors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List professors
         * @description Returns professor directory entries optionally filtered by activation state.
         */
        get: operations["listProfessors"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/professors/{id}/courses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Professor courses
         * @description Retrieves courses assigned to a professor.
         */
        get: operations["professorCourses"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/professors/{id}/sections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Professor sections
         * @description Retrieves sections assigned to a professor.
         */
        get: operations["professorSections"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/professors/{id}/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Professor sessions
         * @description Lists scheduled sessions for a professor.
         */
        get: operations["professorSessions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/recognition": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Recognize face
         * @description Attempts to match a captured face image against enrolled students.
         */
        post: operations["recognize"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/recognition/train": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Train recognition model
         * @description Triggers the face recognition training process for a student cohort.
         */
        post: operations["train"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sections
         * @description Retrieves section summaries with optional course, professor, and activation filters.
         */
        get: operations["listSections"];
        put?: never;
        /**
         * Create section
         * @description Creates a new section for the authenticated professor.
         */
        post: operations["createSection"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sections/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get section
         * @description Loads details for a specific section.
         */
        get: operations["getSection"];
        /**
         * Update section
         * @description Updates section metadata such as schedule and capacity.
         */
        put: operations["updateSection"];
        post?: never;
        /**
         * Archive section
         * @description Archives an existing section.
         */
        delete: operations["deleteSection"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sections/{id}/enrollments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Upsert section enrollment
         * @description Adds or reactivates students in a section roster.
         */
        post: operations["upsertSectionEnrollment"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sections/{id}/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Section sessions
         * @description Lists all sessions scheduled for a section.
         */
        get: operations["sectionSessions"];
        put?: never;
        /**
         * Schedule session
         * @description Schedules a new session for a section.
         */
        post: operations["scheduleSession"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sections/{id}/students": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List section students
         * @description Returns all active students enrolled in a section.
         */
        get: operations["getSectionStudents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sessions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get session details
         * @description Loads metadata for a specific attendance session.
         */
        get: operations["getSession"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sessions/{id}/{action}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Manage session lifecycle
         * @description Transitions a session by invoking start, pause, resume, or stop actions.
         */
        post: operations["manage"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sessions/{id}/attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List session attendance
         * @description Returns attendance records for a session, including student data.
         */
        get: operations["getAttendance"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sessions/{id}/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Subscribe to session events
         * @description Creates a server-sent events stream for session updates.
         */
        get: operations["events"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sessions/{id}/recognition-log": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch recognition log
         * @description Returns recent recognition log entries for a session.
         */
        get: operations["getRecognitionLog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sessions/{id}/students": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List session students
         * @description Retrieves enrolled students for the session's section.
         */
        get: operations["getSessionStudents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch application settings
         * @description Returns passcode requirements for staff tools.
         */
        get: operations["getSettings"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/validate-staff-passcode": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Validate staff passcode
         * @description Verifies whether the submitted staff passcode matches the configured value.
         */
        post: operations["validatePasscode"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/validate-admin-passcode": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Validate admin passcode
         * @description Verifies whether the submitted admin passcode matches the configured value.
         */
        post: operations["validateAdminPasscode"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/storage/face-images/{studentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List face images
         * @description Returns the metadata for all stored face image files for a student.
         */
        get: operations["list"];
        put?: never;
        /**
         * Upload face image
         * @description Uploads a face image for a student with optional upsert semantics.
         */
        post: operations["upload"];
        /**
         * Delete face image(s)
         * @description Deletes one or all stored face image files for a student.
         */
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/storage/face-images/{studentId}/download": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Download face image
         * @description Streams a specific face image file for a student.
         */
        get: operations["download"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/students/{id}/sections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Student sections
         * @description Lists sections a student is enrolled in, enforcing authorization checks.
         */
        get: operations["studentSections"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/connect/{endpoint}/{method}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["serveMultipartEndpoint"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Indicates whether sensitive operations require shared passcodes */
        ApplicationSettingsResponse: {
            /** @description True when professor onboarding actions must be guarded by a shared passcode */
            requiresStaffPasscode?: boolean;
            /** @description True when admin onboarding actions must be guarded by a shared passcode */
            requiresAdminPasscode?: boolean;
        };
        /** @description Payload to create or update attendance records */
        AttendanceUpsertRequest: {
            /**
             * Format: double
             * @description Confidence score for automatically marked attendance
             */
            confidenceScore?: number;
            /**
             * Format: uuid
             * @description Identifier of the attendance record, when updating
             */
            id?: string;
            /** @description How the record was marked (manual, automatic, etc.) */
            markingMethod?: string;
            /** @description Additional notes about the attendance record */
            notes?: string;
            /**
             * Format: uuid
             * @description Session associated with the attendance record
             */
            sessionId?: string;
            /** @description Attendance status value */
            status?: string;
            /**
             * Format: uuid
             * @description Student associated with the attendance record
             */
            studentId?: string;
        };
        AuthSessionResponse: {
            session?: components["schemas"]["SessionDto"];
        };
        /** @description Aggregated administrative view of a course */
        AdminCourseSummary: {
            /**
             * Format: uuid
             * @description Unique identifier of the course
             */
            courseId?: string;
            /** @description Short code identifying the course */
            courseCode?: string;
            /** @description Human readable title for the course */
            courseTitle?: string;
            /** @description Optional description providing more context */
            description?: string;
            /** @description Indicates whether the course is active */
            active?: boolean;
            /**
             * Format: int32
             * @description Number of sections linked to the course
             */
            sectionCount?: number;
            /**
             * Format: int32
             * @description Number of professors assigned to sections of the course
             */
            professorCount?: number;
            /**
             * Format: int32
             * @description Number of distinct students enrolled across all sections
             */
            studentCount?: number;
        };
        /** @description Administrative view of a single section including professor assignment */
        AdminSectionSummary: {
            /**
             * Format: uuid
             * @description Unique identifier of the section
             */
            sectionId?: string;
            /**
             * Format: uuid
             * @description Identifier of the parent course
             */
            courseId?: string;
            /** @description Course code associated with the section */
            courseCode?: string;
            /** @description Course title associated with the section */
            courseTitle?: string;
            /** @description Code used to identify the section */
            sectionCode?: string;
            /**
             * Format: int32
             * @description Scheduled day of week (1 = Monday)
             */
            dayOfWeek?: number;
            /** @description Human readable label for the scheduled meeting day */
            dayLabel?: string;
            /** @description Formatted summary of the meeting time range */
            timeRangeLabel?: string;
            /**
             * Format: time
             * @description Scheduled start time
             */
            startTime?: string;
            /**
             * Format: time
             * @description Scheduled end time
             */
            endTime?: string;
            /** @description Classroom or online location details */
            location?: string;
            /**
             * Format: int32
             * @description Maximum number of students allowed
             */
            maxStudents?: number;
            /**
             * Format: int32
             * @description Current number of enrolled students
             */
            enrolledCount?: number;
            /** @description Enrollment summary including capacity */
            enrollmentSummary?: string;
            /**
             * Format: int32
             * @description Minutes after scheduled start before students are marked late
             */
            lateThresholdMinutes?: number;
            /**
             * Format: uuid
             * @description Identifier of the professor assigned to the section
             */
            professorId?: string;
            /** @description Full name of the professor assigned to the section */
            professorName?: string;
            /** @description Email address of the professor */
            professorEmail?: string;
            /** @description Staff identifier for the professor */
            professorStaffId?: string;
        };
        /** @description Administrative view of a section belonging to a course */
        AdminCourseSection: {
            /**
             * Format: uuid
             * @description Unique identifier of the section
             */
            sectionId?: string;
            /** @description Code used to identify the section */
            sectionCode?: string;
            /**
             * Format: int32
             * @description Scheduled day of week (1 = Monday)
             */
            dayOfWeek?: number;
            /**
             * Format: time
             * @description Scheduled start time
             */
            startTime?: string;
            /**
             * Format: time
             * @description Scheduled end time
             */
            endTime?: string;
            /** @description Location where the section meets */
            location?: string;
            /** @description Indicates whether the section is active */
            active?: boolean;
            /**
             * Format: uuid
             * @description Identifier of the assigned professor
             */
            professorId?: string;
            /** @description Display name of the assigned professor */
            professorName?: string;
            /** @description Email address of the assigned professor */
            professorEmail?: string;
            /**
             * Format: int32
             * @description Number of active students enrolled in the section
             */
            studentCount?: number;
            /**
             * Format: int32
             * @description Number of sessions scheduled for the section
             */
            sessionCount?: number;
            /**
             * Format: double
             * @description Average attendance rate across recorded sessions
             */
            attendanceRate?: number;
        };
        /** @description Administrative student summary for a course */
        AdminCourseStudent: {
            /**
             * Format: uuid
             * @description Identifier of the student profile
             */
            studentId?: string;
            /** @description Display name of the student */
            fullName?: string;
            /** @description Email address of the student */
            email?: string;
            /** @description Institution issued student number */
            studentNumber?: string;
            /**
             * Format: uuid
             * @description Identifier of the section the student is enrolled in
             */
            sectionId?: string;
            /** @description Code of the enrolled section */
            sectionCode?: string;
            /**
             * Format: int32
             * @description Total sessions scheduled for the section
             */
            totalSessions?: number;
            /**
             * Format: int32
             * @description Sessions with recorded attendance for the student
             */
            recordedSessions?: number;
            /**
             * Format: int32
             * @description Sessions the student attended (present or late)
             */
            attendedSessions?: number;
            /**
             * Format: double
             * @description Attendance rate computed from recorded sessions
             */
            attendanceRate?: number;
        };
        /** @description Lightweight view of a course and its activation state */
        CourseSummary: {
            /** @description Indicates whether the course is active */
            active?: boolean;
            /** @description Short code used to reference the course */
            courseCode?: string;
            /** @description Display title of the course */
            courseTitle?: string;
            /** @description Optional detailed description */
            description?: string;
            /**
             * Format: uuid
             * @description Unique identifier of the course
             */
            id?: string;
        };
        /** @description Payload used to create or update a course */
        CreateCourseRequest: {
            /**
             * @description Short code identifying the course
             * @example CS101
             */
            courseCode?: string;
            /**
             * @description Human readable title for the course
             * @example Introduction to Computer Science
             */
            courseTitle?: string;
            /** @description Optional description providing more context */
            description?: string;
        };
        /** @description Payload describing a class section */
        CreateSectionRequest: {
            /**
             * Format: uuid
             * @description Identifier of the parent course
             */
            courseId?: string;
            /**
             * Format: uuid
             * @description Identifier of the professor assigned to the section
             */
            professorId?: string;
            /**
             * Format: int32
             * @description Day of week represented as ISO-8601 number (1=Monday)
             */
            dayOfWeek?: number;
            /**
             * @description Scheduled end time in HH:mm format
             * @example 10:30
             */
            endTime?: string;
            /** @description Classroom or online location details */
            location?: string;
            /**
             * Format: int32
             * @description Maximum number of students allowed
             */
            maxStudents?: number;
            /**
             * Format: int32
             * @description Minutes after scheduled start before students are marked late
             */
            lateThresholdMinutes?: number;
            /**
             * @description Unique section code for the course
             * @example A1
             */
            sectionCode?: string;
            /**
             * @description Scheduled start time in HH:mm format
             * @example 09:00
             */
            startTime?: string;
        };
        /** @description Response containing the authenticated user's profile and roles */
        CurrentUserResponse: {
            /** @description Profile details for the authenticated user */
            profile?: components["schemas"]["Profile"];
            /** @description Authorities granted to the authenticated user */
            roles?: string[];
        };
        DataBuffer: unknown;
        /** @description Metadata describing captured face data for a student */
        FaceData: {
            /**
             * Format: double
             * @description Confidence score assigned to the face sample
             */
            confidence_score?: number;
            /**
             * Format: date-time
             * @description Timestamp when the face data was created
             */
            created_at?: string;
            /** @description Vector representation of the face, when available */
            embedding_vector?: components["schemas"]["JsonNode"];
            /** @description Face encoding produced by recognition algorithms */
            face_encoding?: components["schemas"]["JsonNode"];
            /**
             * Format: uuid
             * @description Identifier of the face data record
             */
            id?: string;
            /** @description URL pointing to the stored image */
            image_url?: string;
            /** @description Indicates whether this face is the primary sample */
            is_primary?: boolean;
            /** @description Additional metadata provided by upstream services */
            metadata?: components["schemas"]["JsonNode"];
            /** @description Processing status within the recognition pipeline */
            processing_status?: string;
            /**
             * Format: double
             * @description Quality score evaluating the face sample
             */
            quality_score?: number;
            /**
             * Format: uuid
             * @description Student identifier associated with the face data
             */
            student_id?: string;
        };
        /** @description Payload for creating face data records */
        FaceDataCreateRequest: {
            /**
             * Format: double
             * @description Confidence score associated with the sample
             */
            confidence_score?: number;
            /** @description Optional embedding vector for the face */
            embedding_vector?: components["schemas"]["JsonNode"];
            /** @description Optional face encoding payload */
            face_encoding?: components["schemas"]["JsonNode"];
            /** @description Public URL of the uploaded face image */
            image_url?: string;
            /** @description Whether the image should be marked as primary */
            is_primary?: boolean;
            /** @description Additional metadata to persist with the face data */
            metadata?: components["schemas"]["JsonNode"];
            /** @description Processing status to persist with the record */
            processing_status?: string;
            /**
             * Format: double
             * @description Quality score of the uploaded image
             */
            quality_score?: number;
        };
        /** @description Payload for deleting face data records */
        FaceDataDeleteRequest: {
            /** @description Identifiers of face data records to delete */
            ids?: string[];
        };
        /** @description Summary status of available face data for a student */
        FaceDataStatus: {
            /** @description Whether any face data exists for the student */
            has_face_data?: boolean;
            /**
             * Format: int32
             * @description Number of stored images
             */
            image_count?: number;
            /** @description Most recent processing status */
            latest_status?: string;
            /**
             * Format: date-time
             * @description Timestamp when status was last updated
             */
            updated_at?: string;
        };
        /** @description Metadata describing a stored face image file */
        FaceImageFile: {
            /** @description Download URL for the stored file */
            download_url?: string;
            /** @description Name of the file in storage */
            file_name?: string;
            /**
             * Format: int64
             * @description Size of the file in bytes
             */
            size_bytes?: number;
            /** @description Storage path where the file resides */
            storage_path?: string;
            /**
             * Format: date-time
             * @description Timestamp when the file was uploaded
             */
            uploaded_at?: string;
        };
        /** @description Response metadata returned after uploading a face image */
        FaceImageUploadResponse: {
            /** @description Pre-signed download URL, when available */
            download_url?: string;
            /** @description Name of the stored file */
            file_name?: string;
            /** @description Publicly accessible URL, when available */
            public_url?: string;
            /** @description Path in storage where the file resides */
            storage_path?: string;
        };
        JsonNode: unknown;
        /** @description Generic paginated response wrapper */
        PagedResponse: {
            items?: components["schemas"]["SessionAttendanceRecord"][];
            /**
             * Format: int32
             * @description Zero-based page index
             */
            page?: number;
            /**
             * Format: int32
             * @description Size of the page requested
             */
            size?: number;
            /**
             * Format: int64
             * @description Total number of items available
             */
            total_items?: number;
            /**
             * Format: int32
             * @description Total number of pages available
             */
            total_pages?: number;
        };
        /** @description Payload containing a staff passcode candidate */
        PasscodeValidationRequest: {
            /** @description Candidate passcode to validate */
            passcode?: string;
        };
        /** @description Result of validating a staff passcode */
        PasscodeValidationResponse: {
            /** @description True when the submitted passcode is valid */
            valid?: boolean;
        };
        /** @description Aggregated metrics for professor dashboards */
        ProfessorDashboardSummary: {
            /**
             * Format: int32
             * @description Count of active sessions in progress
             */
            active_sessions?: number;
            /**
             * Format: int32
             * @description Number of sections assigned to the professor
             */
            total_sections?: number;
            /**
             * Format: int32
             * @description Total students across active sections
             */
            total_students?: number;
            /**
             * Format: int32
             * @description Count of upcoming sessions
             */
            upcoming_sessions?: number;
        };
        /** @description Directory listing details for a professor */
        ProfessorDirectoryEntry: {
            /** @description Whether the professor is currently active */
            active?: boolean;
            /** @description Contact email address */
            email?: string;
            /** @description Display name of the professor */
            fullName?: string;
            /**
             * Format: uuid
             * @description Unique identifier of the professor profile
             */
            id?: string;
            /** @description Staff identifier, if available */
            staffId?: string;
        };
        /** @description Profile information for an authenticated user */
        Profile: {
            /** @description Avatar image URL */
            avatarUrl?: string;
            /** @description Email address associated with the profile */
            email?: string;
            /** @description Full name of the user */
            fullName?: string;
            /**
             * Format: uuid
             * @description Unique identifier of the profile
             */
            id?: string;
            /** @description Primary role of the user */
            role?: string;
            /** @description Staff identifier, when applicable */
            staffId?: string;
            /** @description Student identifier, when applicable */
            studentId?: string;
            /**
             * Format: uuid
             * @description Identifier of the linked authentication user
             */
            userId?: string;
        };
        /** @description Entry generated from recognition events */
        RecognitionLogEntry: {
            /**
             * Format: double
             * @description Confidence score of the recognition
             */
            confidence?: number;
            /** @description Unique key generated for the log entry */
            key?: string;
            /** @description Indicates if manual confirmation is required */
            requires_manual_confirmation?: boolean;
            /**
             * Format: uuid
             * @description Session identifier associated with the recognition event
             */
            session_id?: string;
            /**
             * Format: uuid
             * @description Student identifier recognized in the event
             */
            student_id?: string;
            /** @description Whether the recognition succeeded */
            success?: boolean;
            /**
             * Format: date-time
             * @description Timestamp when the event occurred
             */
            timestamp?: string;
        };
        /** @description Payload submitted for face recognition */
        RecognitionRequest: {
            /**
             * Format: double
             * @description Optional confidence threshold override
             */
            confidenceThreshold?: number;
            /** @description Base64 encoded image data */
            imageData?: string;
            /**
             * Format: uuid
             * @description Session identifier to associate recognition with
             */
            sessionId?: string;
        };
        /** @description Response produced after processing a recognition request */
        RecognitionResponse: {
            /**
             * Format: double
             * @description Confidence score of the recognition
             */
            confidence?: number;
            /** @description Error message when recognition fails */
            error?: string;
            /** @description True when manual confirmation is required */
            requiresManualConfirmation?: boolean;
            /**
             * Format: uuid
             * @description Identifier of the recognized student, when successful
             */
            studentId?: string;
            /** @description Indicates if recognition succeeded */
            success?: boolean;
        };
        /** @description Request body for scheduling a session */
        ScheduleSessionRequest: {
            /**
             * Format: int32
             * @description Minutes after start considered late
             */
            lateThresholdMinutes?: number;
            /** @description Optional location information */
            location?: string;
            /** @description Optional notes visible to instructors */
            notes?: string;
            /**
             * @description Date of the session in ISO-8601 format
             * @example 2024-06-01
             */
            sessionDate?: string;
            /**
             * @description Start time in HH:mm format
             * @example 13:30
             */
            startTime?: string;
        };
        /** @description Enrollment update payload for a section */
        SectionEnrollmentRequest: {
            /** @description Whether to activate existing enrollments instead of removing them */
            activate?: boolean;
            /** @description List of student identifiers to enroll or update */
            student_ids?: string[];
        };
        /** @description Aggregated data about a teaching section */
        SectionSummary: {
            /** @description Course code associated with the section */
            courseCode?: string;
            /** @description Course description associated with the section */
            courseDescription?: string;
            /**
             * Format: uuid
             * @description Identifier of the parent course
             */
            courseId?: string;
            /** @description Course title associated with the section */
            courseTitle?: string;
            /**
             * Format: int32
             * @description Day of week represented as ISO-8601 number (1=Monday)
             */
            dayOfWeek?: number;
            /** @description Scheduled end time */
            endTime?: string;
            /**
             * Format: int32
             * @description Current number of enrolled students
             */
            enrolledCount?: number;
            /**
             * Format: uuid
             * @description Unique identifier of the section
             */
            id?: string;
            /** @description Location information for the section */
            location?: string;
            /**
             * Format: int32
             * @description Maximum number of students
             */
            maxStudents?: number;
            /**
             * Format: int32
             * @description Minutes after scheduled start when students are marked late
             */
            lateThresholdMinutes?: number;
            /** @description Code used to reference the section */
            sectionCode?: string;
            /** @description Scheduled start time */
            startTime?: string;
        };
        /** @description Payload controlling session lifecycle actions */
        SessionActionRequest: {
            /** @description Action to perform (start, pause, resume, stop) */
            action?: string;
            /**
             * Format: uuid
             * @description Professor identifier executing the action
             */
            professorId?: string;
        };
        /** @description Attendance record enriched with student details */
        SessionAttendanceRecord: {
            /**
             * Format: double
             * @description Confidence score from the recognition system
             */
            confidenceScore?: number;
            /**
             * Format: uuid
             * @description Identifier of the parent course for the section
             */
            courseId?: string;
            /** @description Course code associated with the section */
            courseCode?: string;
            /** @description Course title associated with the section */
            courseTitle?: string;
            /**
             * Format: uuid
             * @description Identifier of the attendance record
             */
            id?: string;
            /**
             * Format: date-time
             * @description Last time the student was observed
             */
            lastSeen?: string;
            /**
             * Format: date-time
             * @description Timestamp when attendance was marked
             */
            markedAt?: string;
            /** @description Marking method used to capture the attendance record */
            markingMethod?: string;
            /** @description Instructor notes associated with the record */
            notes?: string;
            /**
             * Format: uuid
             * @description Session identifier associated with the record
             */
            sessionId?: string;
            /**
             * Format: uuid
             * @description Identifier of the section hosting the attendance session
             */
            sectionId?: string;
            /** @description Code used to reference the section */
            sectionCode?: string;
            /** @description Attendance status value */
            status?: string;
            /** @description Student details associated with the attendance record */
            student?: components["schemas"]["Student"];
            /**
             * Format: uuid
             * @description Student identifier associated with the record
             */
            studentId?: string;
        };
        /** @description Detailed representation of an attendance session */
        SessionDetails: {
            /**
             * Format: date-time
             * @description Planned end time with timezone
             */
            endTime?: string;
            /**
             * Format: uuid
             * @description Unique identifier of the session
             */
            id?: string;
            /**
             * Format: int32
             * @description Minutes after start when a student is considered late
             */
            lateThresholdMinutes?: number;
            /** @description Location for the session */
            location?: string;
            /** @description Instructor notes */
            notes?: string;
            /**
             * Format: uuid
             * @description Identifier of the professor supervising the session
             */
            professorId?: string;
            /**
             * Format: uuid
             * @description Identifier of the section containing the session
             */
            sectionId?: string;
            /**
             * Format: date
             * @description Date of the session
             */
            sessionDate?: string;
            /**
             * Format: date-time
             * @description Planned start time with timezone
             */
            startTime?: string;
            /** @description Lifecycle status */
            status?: string;
        };
        SessionDto: {
            accessToken?: string;
            /** Format: int64 */
            expiresAt?: number;
            /** Format: int64 */
            expiresIn?: number;
            tokenType?: string;
            user?: components["schemas"]["SupabaseUserDto"];
        };
        /** @description Summary metrics for an attendance session */
        SessionSummary: {
            /**
             * Format: int32
             * @description Number of attendance records captured (any status)
             */
            attendanceCount?: number;
            /**
             * @description Readable summary of present and late counts
             */
            attendanceSummary?: string;
            /**
             * Format: double
             * @description Attendance rate for the session expressed as a decimal between 0 and 1
             */
            attendanceRate?: number;
            /**
             * Format: int32
             * @description Number of students with an absent mark
             */
            absentCount?: number;
            /** @description Day of week label for the session date */
            dayLabel?: string;
            /**
             * Format: date-time
             * @description Scheduled end timestamp
             */
            endTime?: string;
            /**
             * Format: uuid
             * @description Unique identifier of the session
             */
            id?: string;
            /** @description Location where the session is held */
            location?: string;
            /** @description Additional instructor notes */
            notes?: string;
            /**
             * Format: int32
             * @description Number of students with a late mark
             */
            lateCount?: number;
            /**
             * Format: double
             * @description Late rate for the session expressed as a decimal between 0 and 1
             */
            lateRate?: number;
            /**
             * Format: int32
             * @description Number of students with a present mark
             */
            presentCount?: number;
            /**
             * Format: double
             * @description Present rate for the session expressed as a decimal between 0 and 1
             */
            presentRate?: number;
            /**
             * Format: int32
             * @description Recorded student rows available for the session
             */
            recordedStudents?: number;
            /**
             * Format: uuid
             * @description Identifier of the section hosting the session
             */
            sectionId?: string;
            /**
             * Format: date
             * @description Date the session occurs
             */
            sessionDate?: string;
            /**
             * Format: date-time
             * @description Scheduled start timestamp
             */
            startTime?: string;
            /** @description Current lifecycle status of the session */
            status?: string;
            /**
             * Format: int32
             * @description Total number of students expected
             */
            totalStudents?: number;
            /** @description Formatted summary of the session's time range */
            timeRangeLabel?: string;
        };
        PasswordResetConfirmRequest: {
            accessToken: string;
            password: string;
        };
        PasswordResetRequest: {
            email: string;
        };
        SignInRequest: {
            email: string;
            password: string;
        };
        SignUpRequest: {
            email: string;
            password: string;
            userData?: {
                [key: string]: unknown;
            };
        };
        SseEmitter: {
            /** Format: int64 */
            timeout?: number;
        };
        /** @description Minimal representation of a student profile */
        Student: {
            /** @description URL pointing to the student's avatar image */
            avatarUrl?: string;
            /** @description Display name of the student */
            fullName?: string;
            /**
             * Format: uuid
             * @description Unique identifier of the student
             */
            id?: string;
            /** @description Institution-issued student number */
            studentNumber?: string;
        };
        /** @description Aggregated metrics for student dashboards */
        StudentDashboardSummary: {
            /**
             * Format: double
             * @description Overall attendance rate for the student
             */
            attendance_rate?: number;
            /**
             * Format: int32
             * @description Number of sessions the student attended
             */
            attended_sessions?: number;
            /**
             * Format: int32
             * @description Number of sections the student is enrolled in
             */
            enrolled_sections?: number;
            /**
             * Format: int32
             * @description Number of sessions the student missed
             */
            missed_sessions?: number;
            /**
             * Format: int32
             * @description Count of upcoming sessions
             */
            upcoming_sessions?: number;
        };
        SupabaseUserDto: {
            app_metadata?: {
                [key: string]: unknown;
            };
            email?: string;
            id?: string;
            user_metadata?: {
                [key: string]: unknown;
            };
        };
        /** @description Derived metrics from the training run */
        TrainingMetrics: {
            /**
             * Format: double
             * @description Average embedding distance
             */
            avgDistance?: number;
            /**
             * Format: int32
             * @description Number of samples recognized correctly
             */
            correctSamples?: number;
            /**
             * Format: double
             * @description Maximum embedding distance observed
             */
            maxDistance?: number;
            /**
             * Format: double
             * @description Recognition decision threshold
             */
            recognitionThreshold?: number;
            /**
             * Format: double
             * @description Strong match threshold
             */
            strongThreshold?: number;
            /**
             * Format: double
             * @description Configured decision threshold
             */
            threshold?: number;
            /**
             * Format: int32
             * @description Total number of samples processed
             */
            totalSamples?: number;
        };
        /** @description Payload that triggers a training job */
        TrainingRequest: {
            /**
             * Format: uuid
             * @description Identifier of the student whose model should be trained
             */
            studentId?: string;
            /** @description Collection of test image URLs or base64 strings */
            testImages?: string[];
            /** @description Collection of training image URLs or base64 strings */
            trainingImages?: string[];
        };
        /** @description Result of a recognition training job */
        TrainingResponse: {
            /**
             * Format: double
             * @description Accuracy achieved by the model
             */
            accuracy?: number;
            /**
             * Format: date-time
             * @description Timestamp when training completed
             */
            completedAt?: string;
            /** @description Dataset metadata captured during training */
            dataset?: {
                [key: string]: unknown;
            };
            /** @description Error message if training failed */
            error?: string;
            /** @description Evaluation metrics captured during training */
            evaluation?: {
                [key: string]: unknown;
            };
            /** @description Human readable summary message */
            message?: string;
            /** @description Detailed metrics for the training run */
            metrics?: components["schemas"]["TrainingMetrics"];
            /** @description Storage path to the model artifact */
            modelArtifactPath?: string;
            /** @description Identifier for the generated model artifact */
            modelId?: string;
            /** @description Indicates whether the training met acceptance criteria */
            passed?: boolean;
            /** @description Storage path to the training statistics artifact */
            statsArtifactPath?: string;
            /**
             * Format: uuid
             * @description Identifier of the student associated with the training job
             */
            studentId?: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    search: {
        parameters: {
            query?: {
                /** @description Filter records marked on/after this timestamp */
                from?: string;
                /**
                 * @description Zero-based page index
                 * @example 0
                 */
                page?: number;
                /** @description Filter by section identifier */
                section?: string;
                /** @description Filter by session identifier */
                session?: string;
                /**
                 * @description Page size
                 * @example 25
                 */
                size?: number;
                /**
                 * @description Filter by attendance status
                 * @example present
                 */
                status?: string[];
                /** @description Filter by student identifier */
                student?: string;
                /** @description Filter records marked on/before this timestamp */
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedResponse"];
                };
            };
        };
    };
    upsert: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AttendanceUpsertRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionAttendanceRecord"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AttendanceUpsertRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionAttendanceRecord"];
                };
            };
        };
    };
    confirmPasswordReset: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordResetConfirmRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    requestPasswordReset: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordResetRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    signIn: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SignInRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AuthSessionResponse"];
                };
            };
        };
    };
    signOut: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    signUp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SignUpRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    listCourses: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CourseSummary"][];
                };
            };
        };
    };
    listAdminCourses: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminCourseSummary"][];
                };
            };
        };
    };
    listAdminCourseSections: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminCourseSection"][];
                };
            };
        };
    };
    listAdminCourseStudents: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminCourseStudent"][];
                };
            };
        };
    };
    listAdminSections: {
        parameters: {
            query?: {
                /** @description Optional search string applied to course, section, or professor fields */
                q?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminSectionSummary"][];
                };
            };
        };
    };
    getAdminSection: {
        parameters: {
            query?: never;
            header?: never;
            path?: {
                sectionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdminSectionSummary"];
                };
            };
        };
    };
    updateAdminSection: {
        parameters: {
            query?: never;
            header?: never;
            path?: {
                sectionId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSectionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"];
                };
            };
        };
    };
    createCourse: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateCourseRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CourseSummary"];
                };
            };
        };
    };
    updateCourse: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateCourseRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CourseSummary"];
                };
            };
        };
    };
    deleteCourse: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    professor: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProfessorDashboardSummary"];
                };
            };
        };
    };
    student: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentDashboardSummary"];
                };
            };
        };
    };
    listFaceData: {
        parameters: {
            query?: {
                studentId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FaceData"][];
                };
            };
        };
    };
    deleteFaceData: {
        parameters: {
            query?: {
                studentId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["FaceDataDeleteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    createFaceData: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FaceDataCreateRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FaceData"];
                };
            };
        };
    };
    status: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FaceDataStatus"];
                };
            };
        };
    };
    currentUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CurrentUserResponse"];
                };
            };
        };
    };
    listProfessors: {
        parameters: {
            query?: {
                active?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ProfessorDirectoryEntry"][];
                };
            };
        };
    };
    professorCourses: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CourseSummary"][];
                };
            };
        };
    };
    professorSections: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"][];
                };
            };
        };
    };
    professorSessions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionSummary"][];
                };
            };
        };
    };
    recognize: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RecognitionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RecognitionResponse"];
                };
            };
        };
    };
    train: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TrainingRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TrainingResponse"];
                };
            };
        };
    };
    listSections: {
        parameters: {
            query?: {
                active?: boolean;
                courseId?: string;
                professorId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"][];
                };
            };
        };
    };
    createSection: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSectionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"];
                };
            };
        };
    };
    getSection: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"];
                };
            };
        };
    };
    updateSection: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSectionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"];
                };
            };
        };
    };
    deleteSection: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    upsertSectionEnrollment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SectionEnrollmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Student"][];
                };
            };
        };
    };
    sectionSessions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionSummary"][];
                };
            };
        };
    };
    scheduleSession: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ScheduleSessionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionSummary"];
                };
            };
        };
    };
    getSectionStudents: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Student"][];
                };
            };
        };
    };
    getSession: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionDetails"];
                };
            };
        };
    };
    manage: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                action: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SessionActionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionDetails"];
                };
            };
        };
    };
    getAttendance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionAttendanceRecord"][];
                };
            };
        };
    };
    events: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SseEmitter"];
                };
            };
        };
    };
    getRecognitionLog: {
        parameters: {
            query?: {
                limit?: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RecognitionLogEntry"][];
                };
            };
        };
    };
    getSessionStudents: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["Student"][];
                };
            };
        };
    };
    getSettings: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApplicationSettingsResponse"];
                };
            };
        };
    };
    validatePasscode: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PasscodeValidationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PasscodeValidationResponse"];
                };
            };
        };
    };
    validateAdminPasscode: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PasscodeValidationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PasscodeValidationResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FaceImageFile"][];
                };
            };
        };
    };
    upload: {
        parameters: {
            query?: {
                upsert?: boolean;
            };
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    file: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["FaceImageUploadResponse"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query?: {
                fileName?: string;
            };
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    download: {
        parameters: {
            query: {
                fileName: string;
            };
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DataBuffer"][];
                };
            };
        };
    };
    studentSections: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SectionSummary"][];
                };
            };
        };
    };
    serveMultipartEndpoint: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                endpoint: string;
                method: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": Record<string, never>;
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": string;
                    "application/json;charset=UTF-8": string;
                };
            };
        };
    };
}
