package com.smartattendance.supabase.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartattendance.supabase.dto.CourseSummaryDto;
import com.smartattendance.supabase.dto.CreateCourseRequest;
import com.smartattendance.supabase.dto.CreateSectionRequest;
import com.smartattendance.supabase.dto.ScheduleSessionRequest;
import com.smartattendance.supabase.dto.SectionEnrollmentRequest;
import com.smartattendance.supabase.dto.SectionSummaryDto;
import com.smartattendance.supabase.dto.ProfileDto;
import com.smartattendance.supabase.dto.SessionSummaryDto;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.dto.ProfessorDirectoryEntry;
import com.smartattendance.supabase.dto.StudentSectionDetailDto;
import com.smartattendance.supabase.dto.RosterImportResponse;
import com.smartattendance.supabase.service.profile.ProfileService;
import com.smartattendance.supabase.service.reporting.TeachingManagementService;
import com.smartattendance.supabase.service.section.RosterImportService;
import com.smartattendance.supabase.web.support.AuthenticationResolver;

import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Teaching Management", description = "Course, section, and session administration")
public class TeachingController {

    private final TeachingManagementService teachingManagementService;
    private final AuthenticationResolver authenticationResolver;
    private final ProfileService profileService;
    private final RosterImportService rosterImportService;

    public TeachingController(TeachingManagementService teachingManagementService,
                             AuthenticationResolver authenticationResolver,
                             ProfileService profileService,
                             RosterImportService rosterImportService) {
        this.teachingManagementService = teachingManagementService;
        this.authenticationResolver = authenticationResolver;
        this.profileService = profileService;
        this.rosterImportService = rosterImportService;
    }

    @GetMapping("/professors")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "List professors", description = "Returns professor directory entries optionally filtered by activation state.")
    public List<ProfessorDirectoryEntry> listProfessors(@RequestParam(name = "active", required = false) Boolean active) {
        return teachingManagementService.listProfessors(active);
    }

    @GetMapping("/courses")
    @Operation(summary = "List courses", description = "Lists all active courses available for scheduling.")
    public List<CourseSummaryDto> listCourses() {
        return teachingManagementService.findActiveCourses();
    }

    @GetMapping("/sections")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "List sections", description = "Retrieves section summaries with optional course, professor, and activation filters.")
    public List<SectionSummaryDto> listSections(@RequestParam(name = "active", required = false) Boolean active,
                                                @RequestParam(name = "courseId", required = false) UUID courseId,
                                                @RequestParam(name = "professorId", required = false) UUID professorId) {
        return teachingManagementService.listSections(active, courseId, professorId);
    }

    @PostMapping("/courses")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Create course", description = "Creates a new course that can host sections.")
    public CourseSummaryDto createCourse(@RequestBody CreateCourseRequest request) {
        return teachingManagementService.createCourse(request);
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Update course", description = "Updates course details for the given identifier.")
    public CourseSummaryDto updateCourse(@PathVariable("id") UUID courseId,
                                         @RequestBody CreateCourseRequest request) {
        return teachingManagementService.updateCourse(courseId, request);
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Delete course", description = "Permanently removes a course and its related sections, sessions, and enrollments.")
    public ResponseEntity<Void> deleteCourse(@PathVariable("id") UUID courseId) {
        teachingManagementService.deleteCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/professors/{id}/sections")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Professor sections", description = "Retrieves sections assigned to a professor.")
    public List<SectionSummaryDto> professorSections(@PathVariable("id") UUID professorId,
                                                     @RequestParam(name = "query", required = false) String query,
                                                     @RequestParam(name = "dayOfWeek", required = false) Integer dayOfWeek,
                                                     @RequestParam(name = "courseId", required = false) UUID courseId) {
        if (dayOfWeek != null && (dayOfWeek < 1 || dayOfWeek > 7)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayOfWeek must be between 1 and 7");
        }
        return teachingManagementService.searchSectionsForProfessor(professorId, query, dayOfWeek, courseId);
    }

    @GetMapping("/professors/{id}/courses")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Professor courses", description = "Retrieves courses assigned to a professor.")
    public List<CourseSummaryDto> professorCourses(@PathVariable("id") UUID professorId) {
        return teachingManagementService.findCoursesForProfessor(professorId);
    }

    @GetMapping("/professors/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Professor sessions", description = "Lists scheduled sessions for a professor.")
    public List<SessionSummaryDto> professorSessions(@PathVariable("id") UUID professorId) {
        return teachingManagementService.findSessionsForProfessor(professorId);
    }

    @PostMapping("/sections")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Create section", description = "Creates a new section for the authenticated professor.")
    public SectionSummaryDto createSection(@RequestBody CreateSectionRequest request, Authentication authentication) {
        UUID userId = authenticationResolver.requireUserId(authentication);
        UUID professorProfileId = requireProfessorProfileId(userId);
        return teachingManagementService.createSection(professorProfileId, request);
    }

    @GetMapping("/sections/{id}")
    @Operation(summary = "Get section", description = "Loads details for a specific section.")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    public SectionSummaryDto getSection(@PathVariable("id") UUID sectionId) {
        return teachingManagementService.findSection(sectionId);
    }

    @PutMapping("/sections/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Update section", description = "Updates section metadata such as schedule and capacity.")
    public SectionSummaryDto updateSection(@PathVariable("id") UUID sectionId,
                                           @RequestBody CreateSectionRequest request) {
        return teachingManagementService.updateSection(sectionId, request);
    }

    @DeleteMapping("/sections/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Delete section", description = "Permanently removes the section and all related data.")
    public ResponseEntity<Void> deleteSection(@PathVariable("id") UUID sectionId) {
        teachingManagementService.deleteSection(sectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Schedule session", description = "Schedules a new session for a section.")
    public SessionSummaryDto scheduleSession(@PathVariable("id") UUID sectionId,
                                             @RequestBody ScheduleSessionRequest request,
                                             Authentication authentication) {
        UUID userId = authenticationResolver.requireUserId(authentication);
        UUID professorProfileId = requireProfessorProfileId(userId);
        return teachingManagementService.scheduleSession(professorProfileId, sectionId, request);
    }

    @PostMapping(value = "/sections/roster-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Import roster from file",
            description = "Parses a CSV or XLSX file and returns students that can be added to a section roster.")
    public RosterImportResponse importRoster(@RequestPart("file") MultipartFile file) {
        return rosterImportService.importRoster(file);
    }

    private UUID requireProfessorProfileId(UUID userId) {
        return profileService.findByUserId(userId)
                .map(ProfileDto::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Professor profile not provisioned for authenticated user"));
    }

    @PostMapping("/sections/{id}/enrollments")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Upsert section enrollment", description = "Adds or reactivates students in a section roster.")
    public ResponseEntity<List<StudentDto>> upsertSectionEnrollment(@PathVariable("id") UUID sectionId,
                                                                     @RequestBody(required = false) SectionEnrollmentRequest request) {
        List<UUID> studentIds = request != null ? request.getStudentIds() : null;
        Boolean activate = request != null ? request.getActivate() : null;
        List<StudentDto> roster = teachingManagementService.upsertSectionEnrollments(sectionId, studentIds, activate);
        return ResponseEntity.ok(roster);
    }

    @GetMapping("/sections/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Section sessions", description = "Lists all sessions scheduled for a section.")
    public List<SessionSummaryDto> sectionSessions(@PathVariable("id") UUID sectionId) {
        return teachingManagementService.findSessionsForSection(sectionId);
    }

    @GetMapping("/students/search")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "Search students", description = "Searches active student profiles for enrollment workflows.")
    public List<StudentDto> searchStudents(@RequestParam(name = "q", required = false) String query,
                                          @RequestParam(name = "limit", required = false) Integer limit) {
        return teachingManagementService.searchStudents(query, limit);
    }

    @GetMapping("/students/{id}/sections")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT') or hasAuthority('ROLE_AUTHENTICATED')")
    @Operation(summary = "Student sections", description = "Lists sections a student is enrolled in, enforcing authorization checks.")
    public List<SectionSummaryDto> studentSections(@PathVariable("id") UUID studentId,
                                                  Authentication authentication) {
        assertCanAccessStudent(studentId, authentication);
        return teachingManagementService.findSectionsForStudent(studentId);
    }

    @GetMapping("/students/{studentId}/sections/{sectionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT') or hasAuthority('ROLE_AUTHENTICATED')")
    @Operation(summary = "Student section detail", description = "Returns section schedule and attendance history for the specified student.")
    public StudentSectionDetailDto studentSectionDetail(@PathVariable("studentId") UUID studentId,
                                                        @PathVariable("sectionId") UUID sectionId,
                                                        Authentication authentication) {
        assertCanAccessStudent(studentId, authentication);
        return teachingManagementService.loadStudentSectionDetail(studentId, sectionId);
    }

    private void assertCanAccessStudent(UUID studentId, Authentication authentication) {
        UUID callerId = authenticationResolver.resolveUserId(authentication).orElse(null);
        boolean isAdminOrProfessor = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN") || ga.getAuthority().equals("ROLE_PROFESSOR"));

        boolean isSelfRequest = false;
        if (callerId != null) {
            if (studentId.equals(callerId)) {
                isSelfRequest = true;
            } else {
                isSelfRequest = profileService.findById(studentId)
                        .map(profile -> callerId.equals(profile.getUserId()))
                        .orElse(false);
            }
        }

        if (!isSelfRequest && !isAdminOrProfessor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view sections for this student");
        }
    }
}
