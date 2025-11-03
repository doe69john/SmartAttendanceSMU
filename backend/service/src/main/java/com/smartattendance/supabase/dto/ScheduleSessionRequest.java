package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ScheduleSessionRequest", description = "Request body for scheduling a session")
public class ScheduleSessionRequest {

    @Schema(description = "Date of the session in ISO-8601 format", example = "2024-06-01")
    private String sessionDate;

    @Schema(description = "Start time in ISO-8601 format with offset (e.g. 2024-11-01T16:15:00+08:00) or legacy HH:mm", example = "2024-11-01T16:15:00+08:00")
    private String startTime;

    @Schema(description = "Optional location information")
    private String location;

    @Schema(description = "Optional notes visible to instructors")
    private String notes;

    @Schema(description = "Minutes after start considered late")
    private Integer lateThresholdMinutes;

    public String getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(String sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getLateThresholdMinutes() {
        return lateThresholdMinutes;
    }

    public void setLateThresholdMinutes(Integer lateThresholdMinutes) {
        this.lateThresholdMinutes = lateThresholdMinutes;
    }
}
