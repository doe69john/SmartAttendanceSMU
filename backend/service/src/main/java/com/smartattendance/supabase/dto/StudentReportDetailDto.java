package com.smartattendance.supabase.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentReportDetail", description = "Detailed report for a student including sections and attendance history")
public class StudentReportDetailDto {

    @Schema(description = "Aggregated summary for the student")
    private ProfessorStudentReportDto summary;

    @Schema(description = "Section-level attendance summaries")
    private List<StudentSectionReportDto> sections;

    @Schema(description = "Chronological attendance history")
    private List<StudentAttendanceHistoryDto> attendanceHistory;

    public ProfessorStudentReportDto getSummary() {
        return summary;
    }

    public void setSummary(ProfessorStudentReportDto summary) {
        this.summary = summary;
    }

    public List<StudentSectionReportDto> getSections() {
        return sections;
    }

    public void setSections(List<StudentSectionReportDto> sections) {
        this.sections = sections;
    }

    public List<StudentAttendanceHistoryDto> getAttendanceHistory() {
        return attendanceHistory;
    }

    public void setAttendanceHistory(List<StudentAttendanceHistoryDto> attendanceHistory) {
        this.attendanceHistory = attendanceHistory;
    }
}
