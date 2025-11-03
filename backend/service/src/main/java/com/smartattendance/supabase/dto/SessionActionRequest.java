package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionActionRequest", description = "Payload controlling session lifecycle actions")
public class SessionActionRequest {

    @Schema(description = "Action to perform (start, pause, resume, stop)")
    private String action;

    @Schema(description = "Professor identifier executing the action")
    private UUID professorId;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public void setProfessorId(UUID professorId) {
        this.professorId = professorId;
    }
}
