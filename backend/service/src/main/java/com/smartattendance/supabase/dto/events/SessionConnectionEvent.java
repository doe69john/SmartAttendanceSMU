package com.smartattendance.supabase.dto.events;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionConnectionEvent", description = "Initial event sent when the SSE stream connects")
public class SessionConnectionEvent {

    @Schema(description = "Connection status value")
    private String status;

    public SessionConnectionEvent() {
    }

    public SessionConnectionEvent(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
