package com.smartattendance.supabase.dto.events;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "SessionActionEvent", description = "Event payload broadcast when a session lifecycle action occurs")
public class SessionActionEvent {

    @Schema(description = "Lifecycle action that was executed")
    private String action;

    @Schema(description = "Resulting session status after the action")
    private String status;

    @Schema(description = "Timestamp when the action was processed")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime timestamp;

    public SessionActionEvent() {
    }

    public SessionActionEvent(String action, String status, OffsetDateTime timestamp) {
        this.action = action;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("timestamp")
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
