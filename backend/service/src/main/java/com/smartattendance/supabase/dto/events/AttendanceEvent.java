package com.smartattendance.supabase.dto.events;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AttendanceEvent", description = "Event payload broadcast when an attendance record changes")
public class AttendanceEvent {

    @Schema(description = "Identifier of the student whose attendance was updated")
    @JsonProperty("student_id")
    private UUID studentId;

    @Schema(description = "Resulting attendance status")
    private String status;

    @Schema(description = "Confidence score associated with the mark")
    private Double confidence;

    @Schema(description = "Timestamp when the record was marked")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime timestamp;

    public AttendanceEvent() {
    }

    public AttendanceEvent(UUID studentId, String status, Double confidence, OffsetDateTime timestamp) {
        this.studentId = studentId;
        this.status = status;
        this.confidence = confidence;
        this.timestamp = timestamp;
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

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    @JsonProperty("timestamp")
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
