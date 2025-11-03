package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfessorDirectoryEntry", description = "Directory listing details for a professor")
public class ProfessorDirectoryEntry {

    @Schema(description = "Unique identifier of the professor profile")
    private UUID id;

    @Schema(description = "Display name of the professor")
    private String fullName;

    @Schema(description = "Staff identifier, if available")
    private String staffId;

    @Schema(description = "Contact email address")
    private String email;

    @Schema(description = "Whether the professor is currently active")
    private Boolean active;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
