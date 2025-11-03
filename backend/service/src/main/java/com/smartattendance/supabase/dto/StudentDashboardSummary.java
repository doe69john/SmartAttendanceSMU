package com.smartattendance.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentDashboardSummary", description = "Aggregated metrics for student dashboards")
public class StudentDashboardSummary {

    @JsonProperty("enrolled_sections")
    @Schema(description = "Number of sections the student is enrolled in")
    private int enrolledSections;

    @JsonProperty("upcoming_sessions")
    @Schema(description = "Count of upcoming sessions")
    private int upcomingSessions;

    @JsonProperty("attended_sessions")
    @Schema(description = "Number of sessions the student attended")
    private int attendedSessions;

    @JsonProperty("missed_sessions")
    @Schema(description = "Number of sessions the student missed")
    private int missedSessions;

    @JsonProperty("attendance_rate")
    @Schema(description = "Overall attendance rate for the student")
    private double attendanceRate;

    public int getEnrolledSections() {
        return enrolledSections;
    }

    public void setEnrolledSections(int enrolledSections) {
        this.enrolledSections = enrolledSections;
    }

    public int getUpcomingSessions() {
        return upcomingSessions;
    }

    public void setUpcomingSessions(int upcomingSessions) {
        this.upcomingSessions = upcomingSessions;
    }

    public int getAttendedSessions() {
        return attendedSessions;
    }

    public void setAttendedSessions(int attendedSessions) {
        this.attendedSessions = attendedSessions;
    }

    public int getMissedSessions() {
        return missedSessions;
    }

    public void setMissedSessions(int missedSessions) {
        this.missedSessions = missedSessions;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }
}
