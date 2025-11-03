package com.smartattendance.supabase.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionEnrollmentRequest", description = "Enrollment update payload for a section")
public class SectionEnrollmentRequest {

    @JsonProperty("student_ids")
    @JsonAlias("studentIds")
    @Schema(description = "List of student identifiers to enroll or update")
    private List<UUID> studentIds;

    @JsonProperty("activate")
    @Schema(description = "Whether to activate existing enrollments instead of removing them")
    private Boolean activate;

    public List<UUID> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<UUID> studentIds) {
        this.studentIds = studentIds;
    }

    public Boolean getActivate() {
        return activate;
    }

    public void setActivate(Boolean activate) {
        this.activate = activate;
    }
}
