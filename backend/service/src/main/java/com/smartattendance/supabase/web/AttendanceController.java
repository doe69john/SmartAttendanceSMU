package com.smartattendance.supabase.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.AttendanceUpsertRequest;
import com.smartattendance.supabase.dto.PagedResponse;
import com.smartattendance.supabase.dto.SessionAttendanceRecord;
import com.smartattendance.supabase.service.attendance.AttendanceService;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance", description = "Manage attendance records and manual overrides")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    @Operation(summary = "Search attendance records", description = "Retrieves paginated attendance records filtered by session, section, student or date range.")
    public PagedResponse<SessionAttendanceRecord> search(
            @Parameter(name = "session", in = ParameterIn.QUERY, description = "Filter by session identifier")
            @RequestParam(value = "session", required = false) UUID sessionId,
            @Parameter(name = "student", in = ParameterIn.QUERY, description = "Filter by student identifier")
            @RequestParam(value = "student", required = false) UUID studentId,
            @Parameter(name = "section", in = ParameterIn.QUERY, description = "Filter by section identifier")
            @RequestParam(value = "section", required = false) UUID sectionId,
            @Parameter(name = "status", in = ParameterIn.QUERY, description = "Filter by attendance status", example = "present")
            @RequestParam(value = "status", required = false) List<String> statuses,
            @Parameter(name = "from", in = ParameterIn.QUERY, description = "Filter records marked on/after this timestamp")
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(name = "to", in = ParameterIn.QUERY, description = "Filter records marked on/before this timestamp")
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size", example = "25")
            @RequestParam(value = "size", required = false) Integer size) {
        return attendanceService.search(sessionId, sectionId, studentId, statuses, from, to, page, size);
    }

    @PostMapping
    @Operation(summary = "Upsert attendance record", description = "Creates or updates an attendance record based on the supplied identifiers.")
    public SessionAttendanceRecord upsert(@RequestBody AttendanceUpsertRequest request) {
        return attendanceService.upsert(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update attendance record", description = "Applies partial updates to an existing attendance record.")
    public SessionAttendanceRecord update(@PathVariable("id") UUID recordId, @RequestBody AttendanceUpsertRequest request) {
        return attendanceService.update(recordId, request);
    }
}
