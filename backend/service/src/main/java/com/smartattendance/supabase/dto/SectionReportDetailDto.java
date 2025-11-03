package com.smartattendance.supabase.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionReportDetail", description = "Detailed report for a professor's section")
public class SectionReportDetailDto {

    @Schema(description = "Aggregated summary for the section")
    private ProfessorSectionReportDto summary;

    @Schema(description = "Scheduled sessions for the section")
    private List<SessionSummaryDto> sessions;

    public ProfessorSectionReportDto getSummary() {
        return summary;
    }

    public void setSummary(ProfessorSectionReportDto summary) {
        this.summary = summary;
    }

    public List<SessionSummaryDto> getSessions() {
        return sessions;
    }

    public void setSessions(List<SessionSummaryDto> sessions) {
        this.sessions = sessions;
    }
}
