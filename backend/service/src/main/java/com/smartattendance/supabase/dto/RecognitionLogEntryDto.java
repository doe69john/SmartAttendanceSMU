package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RecognitionLogEntry", description = "Entry generated from recognition events")
public class RecognitionLogEntryDto {

    @JsonProperty("key")
    @Schema(description = "Unique key generated for the log entry")
    private String key;

    @JsonProperty("session_id")
    @Schema(description = "Session identifier associated with the recognition event")
    private UUID sessionId;

    @JsonProperty("student_id")
    @Schema(description = "Student identifier recognized in the event")
    private UUID studentId;

    @JsonProperty("confidence")
    @Schema(description = "Confidence score of the recognition")
    private Double confidence;

    @JsonProperty("success")
    @Schema(description = "Whether the recognition succeeded")
    private Boolean success;

    @JsonProperty("requires_manual_confirmation")
    @Schema(description = "Indicates if manual confirmation is required")
    private Boolean requiresManualConfirmation;

    @JsonProperty("type")
    @Schema(description = "Raw event type string forwarded by the companion app")
    private String type;

    @JsonProperty("message")
    @Schema(description = "Human readable message describing the event outcome")
    private String message;

    @JsonProperty("track_id")
    @Schema(description = "Identifier of the face track associated with the recognition")
    private String trackId;

    @JsonProperty("timestamp")
    @Schema(description = "Timestamp when the event occurred")
    private OffsetDateTime timestamp;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Boolean getRequiresManualConfirmation() {
        return requiresManualConfirmation;
    }

    public void setRequiresManualConfirmation(Boolean requiresManualConfirmation) {
        this.requiresManualConfirmation = requiresManualConfirmation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
