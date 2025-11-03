package com.smartattendance.supabase.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentAttendanceHistory", description = "Detailed attendance record for a student across sessions")
public class StudentAttendanceHistoryDto {

    @Schema(description = "Session identifier")
    private UUID sessionId;

    @Schema(description = "Section identifier")
    private UUID sectionId;

    @Schema(description = "Section code")
    private String sectionCode;

    @Schema(description = "Course code")
    private String courseCode;

    @Schema(description = "Course title")
    private String courseTitle;

    @Schema(description = "Session date")
    private LocalDate sessionDate;

    @Schema(description = "Scheduled start time")
    private OffsetDateTime startTime;

    @Schema(description = "Scheduled end time")
    private OffsetDateTime endTime;

    @Schema(description = "Attendance status")
    private String status;

    @Schema(description = "Timestamp when the attendance was marked")
    private OffsetDateTime markedAt;

    @Schema(description = "Marking method for the attendance record")
    private String markingMethod;

    @Schema(description = "Location of the session")
    private String location;

    @Schema(description = "Optional notes attached to the attendance record")
    private String notes;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = sectionId;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(OffsetDateTime markedAt) {
        this.markedAt = markedAt;
    }

    public String getMarkingMethod() {
        return markingMethod;
    }

    public void setMarkingMethod(String markingMethod) {
        this.markingMethod = markingMethod;
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
}
