package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AttendanceUpsertRequest", description = "Payload to create or update attendance records")
public class AttendanceUpsertRequest {

    @Schema(description = "Identifier of the attendance record, when updating")
    private UUID id;

    @Schema(description = "Session associated with the attendance record")
    private UUID sessionId;

    @Schema(description = "Student associated with the attendance record")
    private UUID studentId;

    @Schema(description = "Attendance status value")
    private String status;

    @Schema(description = "Confidence score for automatically marked attendance")
    private Double confidenceScore;

    @Schema(description = "How the record was marked (manual, automatic, etc.)")
    private String markingMethod;

    @Schema(description = "Additional notes about the attendance record")
    private String notes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getMarkingMethod() {
        return markingMethod;
    }

    public void setMarkingMethod(String markingMethod) {
        this.markingMethod = markingMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
