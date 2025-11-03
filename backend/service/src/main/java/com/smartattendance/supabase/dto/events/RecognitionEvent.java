package com.smartattendance.supabase.dto.events;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RecognitionEvent", description = "Event payload emitted when live recognition finishes")
public class RecognitionEvent {

    @Schema(description = "Identifier of the recognized student, when present")
    @JsonProperty("student_id")
    private UUID studentId;

    @Schema(description = "Confidence score reported by the companion recognizer")
    private Double confidence;

    @Schema(description = "Indicates whether the recognition was successful")
    private boolean success;

    @Schema(description = "True when manual confirmation is required")
    @JsonProperty("requires_manual_confirmation")
    private boolean requiresManualConfirmation;

    @Schema(description = "Categorised type of recognition lifecycle event")
    private String type;

    @Schema(description = "Human-friendly description of the recognition outcome")
    private String message;

    @Schema(description = "Identifier of the recognition track inside the companion runtime")
    @JsonProperty("track_id")
    private String trackId;

    @Schema(description = "Timestamp when the recognition completed")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime timestamp;

    public RecognitionEvent() {
    }

    public RecognitionEvent(UUID studentId, Double confidence, boolean success,
                            boolean requiresManualConfirmation, OffsetDateTime timestamp) {
        this.studentId = studentId;
        this.confidence = confidence;
        this.success = success;
        this.requiresManualConfirmation = requiresManualConfirmation;
        this.timestamp = timestamp;
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

    @JsonProperty("timestamp")
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
