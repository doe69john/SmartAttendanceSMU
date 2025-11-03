package com.smartattendance.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfessorDashboardSummary", description = "Aggregated metrics for professor dashboards")
public class ProfessorDashboardSummary {

    @JsonProperty("total_sections")
    @Schema(description = "Number of sections assigned to the professor")
    private int totalSections;

    @JsonProperty("total_students")
    @Schema(description = "Total students across active sections")
    private int totalStudents;

    @JsonProperty("upcoming_sessions")
    @Schema(description = "Count of upcoming sessions")
    private int upcomingSessions;

    @JsonProperty("active_sessions")
    @Schema(description = "Count of active sessions in progress")
    private int activeSessions;

    public int getTotalSections() {
        return totalSections;
    }

    public void setTotalSections(int totalSections) {
        this.totalSections = totalSections;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public int getUpcomingSessions() {
        return upcomingSessions;
    }

    public void setUpcomingSessions(int upcomingSessions) {
        this.upcomingSessions = upcomingSessions;
    }

    public int getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(int activeSessions) {
        this.activeSessions = activeSessions;
    }
}
