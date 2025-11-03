package com.smartattendance.supabase.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.dto.ProfessorSectionReportDto;
import com.smartattendance.supabase.dto.ProfessorStudentReportDto;
import com.smartattendance.supabase.dto.SectionReportDetailDto;
import com.smartattendance.supabase.dto.StudentReportDetailDto;
import com.smartattendance.supabase.service.profile.ProfileService;
import com.smartattendance.supabase.service.reporting.ReportExport;
import com.smartattendance.supabase.service.reporting.ReportingService;
import com.smartattendance.supabase.service.reporting.ReportingService.ExportFormat;
import com.smartattendance.supabase.web.support.AuthenticationResolver;
import com.smartattendance.supabase.dto.ProfileDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reporting", description = "Aggregated attendance reports for professors")
public class ReportingController {

    private final ReportingService reportingService;
    private final AuthenticationResolver authenticationResolver;
    private final ProfileService profileService;

    public ReportingController(ReportingService reportingService,
                               AuthenticationResolver authenticationResolver,
                               ProfileService profileService) {
        this.reportingService = reportingService;
        this.authenticationResolver = authenticationResolver;
        this.profileService = profileService;
    }

    @GetMapping("/professor/students")
    @PreAuthorize("hasRole('PROFESSOR')")
    @Operation(summary = "List students", description = "Lists students taught by the authenticated professor with aggregate metrics")
    public List<ProfessorStudentReportDto> listProfessorStudents(@RequestParam(name = "query", required = false) String query,
                                                                 Authentication authentication) {
        UUID professorId = requireProfessorProfileId(authentication);
        return reportingService.listProfessorStudents(professorId, query);
    }

    @GetMapping("/admin/students")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List students", description = "Lists all students with campus-wide attendance metrics")
    public List<ProfessorStudentReportDto> listAdminStudents(@RequestParam(name = "query", required = false) String query) {
        return reportingService.listAdminStudents(query);
    }

    @GetMapping("/professor/students/{studentId}")
    @PreAuthorize("hasRole('PROFESSOR')")
    @Operation(summary = "Student report", description = "Loads detailed attendance history for a student under the professor")
    public StudentReportDetailDto getStudentReport(@PathVariable("studentId") UUID studentId,
                                                   Authentication authentication) {
        UUID professorId = requireProfessorProfileId(authentication);
        return reportingService.loadStudentReport(professorId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not associated with professor"));
    }

    @GetMapping("/admin/students/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Student report", description = "Loads detailed attendance history for any student")
    public StudentReportDetailDto getAdminStudentReport(@PathVariable("studentId") UUID studentId) {
        return reportingService.loadStudentReportForAdmin(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    @GetMapping("/professor/students/{studentId}/export")
    @PreAuthorize("hasRole('PROFESSOR')")
    @Operation(summary = "Export student report", description = "Downloads the student's attendance history in CSV or XLSX format")
    public ResponseEntity<byte[]> exportStudentReport(@PathVariable("studentId") UUID studentId,
                                                       @RequestParam(name = "format", required = false) String format,
                                                       Authentication authentication) {
        UUID professorId = requireProfessorProfileId(authentication);
        ExportFormat resolved = ExportFormat.fromString(format);
        ReportExport export = reportingService.createStudentExport(professorId, studentId, resolved)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not associated with professor"));
        return buildFileResponse(export);
    }

    @GetMapping("/admin/students/{studentId}/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export student report", description = "Downloads a student's attendance history in CSV or XLSX format")
    public ResponseEntity<byte[]> exportAdminStudentReport(@PathVariable("studentId") UUID studentId,
                                                           @RequestParam(name = "format", required = false) String format) {
        ExportFormat resolved = ExportFormat.fromString(format);
        ReportExport export = reportingService.createStudentExportForAdmin(studentId, resolved)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        return buildFileResponse(export);
    }

    @GetMapping("/professor/sections")
    @PreAuthorize("hasRole('PROFESSOR')")
    @Operation(summary = "List sections", description = "Lists sections taught by the professor with attendance analytics")
    public List<ProfessorSectionReportDto> listProfessorSections(@RequestParam(name = "query", required = false) String query,
                                                                 Authentication authentication) {
        UUID professorId = requireProfessorProfileId(authentication);
        return reportingService.listProfessorSections(professorId, query);
    }

    @GetMapping("/admin/sections")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List sections", description = "Lists every section with attendance analytics for administrators")
    public List<ProfessorSectionReportDto> listAdminSections(@RequestParam(name = "query", required = false) String query) {
        return reportingService.listAdminSections(query);
    }

    @GetMapping("/professor/sections/{sectionId}")
    @PreAuthorize("hasRole('PROFESSOR')")
    @Operation(summary = "Section report", description = "Loads section analytics and historical sessions")
    public SectionReportDetailDto getSectionReport(@PathVariable("sectionId") UUID sectionId,
                                                   Authentication authentication) {
        UUID professorId = requireProfessorProfileId(authentication);
        return reportingService.loadSectionReport(professorId, sectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not associated with professor"));
    }

    @GetMapping("/admin/sections/{sectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Section report", description = "Loads section analytics and historical sessions for administrators")
    public SectionReportDetailDto getAdminSectionReport(@PathVariable("sectionId") UUID sectionId) {
        return reportingService.loadSectionReportForAdmin(sectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
    }

    @GetMapping("/admin/sections/{sectionId}/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export section report", description = "Downloads section attendance history in CSV or XLSX format")
    public ResponseEntity<byte[]> exportAdminSectionReport(@PathVariable("sectionId") UUID sectionId,
                                                           @RequestParam(name = "format", required = false) String format,
                                                           @RequestParam(name = "sessionId", required = false) UUID sessionId) {
        ExportFormat resolved = ExportFormat.fromString(format);
        ReportExport export = reportingService.createSectionExportForAdmin(sectionId, resolved, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
        return buildFileResponse(export);
    }

    @GetMapping("/professor/sections/{sectionId}/export")
    @PreAuthorize("hasRole('PROFESSOR') or hasRole('ADMIN')")
    @Operation(summary = "Export section report", description = "Downloads section attendance history in CSV or XLSX format")
    public ResponseEntity<byte[]> exportSectionReport(@PathVariable("sectionId") UUID sectionId,
                                                       @RequestParam(name = "format", required = false) String format,
                                                       @RequestParam(name = "sessionId", required = false) UUID sessionId,
                                                       Authentication authentication) {
        ProfileDto profile = requireProfile(authentication);
        ExportFormat resolved = ExportFormat.fromString(format);
        ReportExport export;
        if (profile.getRole() != null && "admin".equalsIgnoreCase(profile.getRole())) {
            export = reportingService.createSectionExportForAdmin(sectionId, resolved, sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
        } else if (profile.getRole() != null && "professor".equalsIgnoreCase(profile.getRole())) {
            export = reportingService.createSectionExport(profile.getId(), sectionId, resolved, sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not associated with professor"));
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only professors or administrators can access reporting data");
        }
        return buildFileResponse(export);
    }

    @GetMapping("/student/export")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Export personal attendance", description = "Downloads the authenticated student's attendance history")
    public ResponseEntity<byte[]> exportStudentSelf(@RequestParam(name = "format", required = false) String format,
                                                    @RequestParam(name = "sectionId", required = false) UUID sectionId,
                                                    Authentication authentication) {
        UUID studentId = requireStudentProfileId(authentication);
        ExportFormat resolved = ExportFormat.fromString(format);
        ReportExport export = reportingService.createStudentSelfExport(studentId, resolved, sectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance records not found"));
        return buildFileResponse(export);
    }

    private ResponseEntity<byte[]> buildFileResponse(ReportExport export) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, export.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.getFileName() + "\"")
                .body(export.getContent());
    }

    private UUID requireProfessorProfileId(Authentication authentication) {
        UUID userId = authenticationResolver.requireUserId(authentication);
        ProfileDto profile = profileService.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Professor profile not provisioned for authenticated user"));
        if (profile.getRole() == null || !"professor".equalsIgnoreCase(profile.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only professors can access reporting data");
        }
        return profile.getId();
    }

    private ProfileDto requireProfile(Authentication authentication) {
        UUID userId = authenticationResolver.requireUserId(authentication);
        return profileService.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Profile not provisioned for authenticated user"));
    }

    private UUID requireStudentProfileId(Authentication authentication) {
        UUID userId = authenticationResolver.requireUserId(authentication);
        ProfileDto profile = profileService.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Student profile not provisioned for authenticated user"));
        if (profile.getRole() == null || !"student".equalsIgnoreCase(profile.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can access student reports");
        }
        return profile.getId();
    }
}
