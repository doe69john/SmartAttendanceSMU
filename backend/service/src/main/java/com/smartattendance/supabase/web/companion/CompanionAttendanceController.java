package com.smartattendance.supabase.web.companion;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.dto.AttendanceUpsertRequest;
import com.smartattendance.supabase.dto.SessionAttendanceRecord;
import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.service.attendance.AttendanceService;
import com.smartattendance.supabase.service.session.SessionQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/companion")
@Tag(name = "Companion Attendance", description = "Record attendance updates submitted by the native companion app")
public class CompanionAttendanceController {

    private final AttendanceService attendanceService;
    private final SessionQueryService sessionQueryService;

    public CompanionAttendanceController(AttendanceService attendanceService,
                                         SessionQueryService sessionQueryService) {
        this.attendanceService = attendanceService;
        this.sessionQueryService = sessionQueryService;
    }

    @PostMapping("/sections/{sectionId}/sessions/{sessionId}/attendance")
    @Operation(summary = "Submit attendance from the companion app",
            description = "Creates or updates an attendance record for the supplied session and student.")
    public SessionAttendanceRecord upsertAttendance(@PathVariable("sectionId") UUID sectionId,
                                                    @PathVariable("sessionId") UUID sessionId,
                                                    @RequestBody CompanionAttendanceRequest request) {
        if (request == null || request.studentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required");
        }
        AttendanceUpsertRequest upsert = new AttendanceUpsertRequest();
        upsert.setSessionId(sessionId);
        upsert.setStudentId(request.studentId());
        upsert.setStatus(request.status());
        upsert.setConfidenceScore(request.confidenceScore());
        upsert.setMarkingMethod(request.markingMethod());
        upsert.setNotes(request.notes());
        return attendanceService.upsert(upsert);
    }

    @GetMapping("/sections/{sectionId}/sessions/{sessionId}/roster")
    @Operation(summary = "Fetch the current attendance roster for the companion app",
            description = "Returns the attendance records for the specified session, including student metadata.")
    public List<SessionAttendanceRecord> fetchRoster(@PathVariable("sectionId") UUID sectionId,
                                                     @PathVariable("sessionId") UUID sessionId) {
        SessionDetailsDto session = sessionQueryService.loadSession(sessionId);
        if (session.getSectionId() != null && !session.getSectionId().equals(sectionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session does not belong to section");
        }
        return sessionQueryService.loadAttendance(sessionId);
    }

    public record CompanionAttendanceRequest(
            UUID studentId,
            String status,
            Double confidenceScore,
            String markingMethod,
            String notes) {
    }
}
