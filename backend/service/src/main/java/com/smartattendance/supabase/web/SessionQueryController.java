package com.smartattendance.supabase.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.SectionAnalyticsDto;
import com.smartattendance.supabase.dto.SessionAttendanceRecord;
import com.smartattendance.supabase.dto.SessionAttendanceStatsDto;
import com.smartattendance.supabase.dto.RecognitionLogEntryDto;
import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.service.session.SessionQueryService;
import com.smartattendance.supabase.web.support.BackendBaseUrlResolver;

@RestController
@RequestMapping("/api")
@Tag(name = "Session Queries", description = "Read-only views of sessions, attendance, and recognition logs")
public class SessionQueryController {

    private final SessionQueryService sessionQueryService;
    private final BackendBaseUrlResolver backendBaseUrlResolver;

    public SessionQueryController(SessionQueryService sessionQueryService,
                                 BackendBaseUrlResolver backendBaseUrlResolver) {
        this.sessionQueryService = sessionQueryService;
        this.backendBaseUrlResolver = backendBaseUrlResolver;
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get session details", description = "Loads metadata for a specific attendance session.")
    public SessionDetailsDto getSession(@PathVariable("id") UUID sessionId,
                                        HttpServletRequest request) {
        SessionDetailsDto dto = sessionQueryService.loadSession(sessionId);
        if (dto != null) {
            dto.setBackendBaseUrl(backendBaseUrlResolver.resolve(request));
        }
        return dto;
    }

    @GetMapping("/sessions/{id}/attendance")
    @Operation(summary = "List session attendance", description = "Returns attendance records for a session, including student data.")
    public List<SessionAttendanceRecord> getAttendance(@PathVariable("id") UUID sessionId) {
        return sessionQueryService.loadAttendance(sessionId);
    }

    @GetMapping("/sessions/{id}/stats")
    @Operation(summary = "Session attendance statistics", description = "Returns aggregated counts for session attendance statuses and capture methods.")
    public SessionAttendanceStatsDto getAttendanceStats(@PathVariable("id") UUID sessionId) {
        return sessionQueryService.loadAttendanceStats(sessionId);
    }

    @GetMapping("/sessions/{id}/students")
    @Operation(summary = "List session students", description = "Retrieves enrolled students for the session's section.")
    public List<StudentDto> getSessionStudents(@PathVariable("id") UUID sessionId) {
        return sessionQueryService.loadSessionStudents(sessionId);
    }

    @GetMapping("/sessions/{id}/recognition-log")
    @Operation(summary = "Fetch recognition log", description = "Returns recent recognition log entries for a session.")
    public List<RecognitionLogEntryDto> getRecognitionLog(@PathVariable("id") UUID sessionId,
                                                          @RequestParam(value = "limit", required = false) Integer limit) {
        return sessionQueryService.loadRecognitionLog(sessionId, limit);
    }

    @GetMapping("/sections/{id}/students")
    @Operation(summary = "List section students", description = "Returns all active students enrolled in a section.")
    public List<StudentDto> getSectionStudents(@PathVariable("id") UUID sectionId) {
        return sessionQueryService.loadSectionStudents(sectionId);
    }

    @GetMapping("/sections/{id}/analytics")
    @Operation(summary = "Section analytics", description = "Returns aggregated metrics for a section including session counts and attendance rates.")
    public SectionAnalyticsDto getSectionAnalytics(@PathVariable("id") UUID sectionId) {
        return sessionQueryService.loadSectionAnalytics(sectionId);
    }
}
