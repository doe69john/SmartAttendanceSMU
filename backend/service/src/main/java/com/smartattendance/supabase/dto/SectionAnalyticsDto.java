package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionAnalytics", description = "Aggregated metrics for a teaching section")
public class SectionAnalyticsDto {

    @Schema(description = "Total number of sessions scheduled for the section")
    private int totalSessions;

    @Schema(description = "Number of sessions that have been completed")
    private int completedSessions;

    @Schema(description = "Number of upcoming scheduled sessions")
    private int upcomingSessions;

    @Schema(description = "Average attendance rate across completed sessions")
    private double averageAttendanceRate;

    @Schema(description = "Average present rate across completed sessions")
    private double averagePresentRate;

    @Schema(description = "Average late rate across completed sessions")
    private double averageLateRate;

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(int completedSessions) {
        this.completedSessions = completedSessions;
    }

    public int getUpcomingSessions() {
        return upcomingSessions;
    }

    public void setUpcomingSessions(int upcomingSessions) {
        this.upcomingSessions = upcomingSessions;
    }

    public double getAverageAttendanceRate() {
        return averageAttendanceRate;
    }

    public void setAverageAttendanceRate(double averageAttendanceRate) {
        this.averageAttendanceRate = averageAttendanceRate;
    }

    public double getAveragePresentRate() {
        return averagePresentRate;
    }

    public void setAveragePresentRate(double averagePresentRate) {
        this.averagePresentRate = averagePresentRate;
    }

    public double getAverageLateRate() {
        return averageLateRate;
    }

    public void setAverageLateRate(double averageLateRate) {
        this.averageLateRate = averageLateRate;
    }
}
