package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CourseSummary", description = "Lightweight view of a course and its activation state")
public class CourseSummaryDto {

    @Schema(description = "Unique identifier of the course")
    private UUID id;

    @Schema(description = "Short code used to reference the course")
    private String courseCode;

    @Schema(description = "Display title of the course")
    private String courseTitle;

    @Schema(description = "Optional detailed description")
    private String description;

    @Schema(description = "Indicates whether the course is active")
    private boolean active;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
