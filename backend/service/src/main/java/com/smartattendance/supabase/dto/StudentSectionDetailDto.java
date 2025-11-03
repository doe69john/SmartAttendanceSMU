package com.smartattendance.supabase.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentSectionDetail", description = "Section overview and student-specific attendance history")
public class StudentSectionDetailDto {

    @Schema(description = "Summary of the section, sanitized for student consumption")
    private SectionSummaryDto section;

    @Schema(description = "Attendance history for the requesting student within the section")
    private List<StudentAttendanceHistoryDto> attendanceHistory;

    public SectionSummaryDto getSection() {
        return section;
    }

    public void setSection(SectionSummaryDto section) {
        this.section = section;
    }

    public List<StudentAttendanceHistoryDto> getAttendanceHistory() {
        return attendanceHistory;
    }

    public void setAttendanceHistory(List<StudentAttendanceHistoryDto> attendanceHistory) {
        this.attendanceHistory = attendanceHistory;
    }
}
