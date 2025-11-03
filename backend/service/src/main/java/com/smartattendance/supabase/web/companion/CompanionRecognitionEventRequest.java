package com.smartattendance.supabase.web.companion;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CompanionRecognitionEventRequest {

    private String studentId;
    private Double confidence;
    private boolean success;
    @JsonProperty("requiresManualConfirmation")
    private boolean requiresManualConfirmation;
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime timestamp;
    private String type;
    private String message;
    private String trackId;

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isRequiresManualConfirmation() {
        return requiresManualConfirmation;
    }

    public void setRequiresManualConfirmation(boolean requiresManualConfirmation) {
        this.requiresManualConfirmation = requiresManualConfirmation;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
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
}
