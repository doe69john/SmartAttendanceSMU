package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateCourseRequest", description = "Payload used to create or update a course")
public class CreateCourseRequest {

    @Schema(description = "Short code identifying the course", example = "CS101")
    private String courseCode;

    @Schema(description = "Human readable title for the course", example = "Introduction to Computer Science")
    private String courseTitle;

    @Schema(description = "Optional description providing more context")
    private String description;

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
}
