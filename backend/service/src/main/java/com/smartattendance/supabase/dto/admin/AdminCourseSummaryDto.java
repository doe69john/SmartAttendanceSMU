package com.smartattendance.supabase.dto.admin;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminCourseSummary", description = "Aggregated overview of a course for administrators")
public class AdminCourseSummaryDto {

    @Schema(description = "Unique identifier of the course", format = "uuid")
    private UUID courseId;

    @Schema(description = "Institution issued course code")
    private String courseCode;

    @Schema(description = "Display title of the course")
    private String courseTitle;

    @Schema(description = "Optional course description")
    private String description;

    @Schema(description = "Whether the course is currently active")
    private boolean active;

    @Schema(description = "Number of sections associated with the course")
    private int sectionCount;

    @Schema(description = "Number of distinct professors teaching the course")
    private int professorCount;

    @Schema(description = "Number of distinct students enrolled across all sections")
    private int studentCount;

    public UUID getCourseId() {
        return courseId;
    }

    public void setCourseId(UUID courseId) {
        this.courseId = courseId;
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

    public int getSectionCount() {
        return sectionCount;
    }

    public void setSectionCount(int sectionCount) {
        this.sectionCount = sectionCount;
    }

    public int getProfessorCount() {
        return professorCount;
    }

    public void setProfessorCount(int professorCount) {
        this.professorCount = professorCount;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(int studentCount) {
        this.studentCount = studentCount;
    }
}
