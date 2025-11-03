package com.smartattendance.supabase.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateSectionRequest", description = "Payload describing a class section")
public class CreateSectionRequest {

    @Schema(description = "Identifier of the parent course")
    private UUID courseId;

    @Schema(description = "Identifier of the professor assigned to the section")
    @JsonProperty("professor_id")
    @JsonAlias("professorId")
    private UUID professorId;

    @Schema(description = "Unique section code for the course", example = "A1")
    private String sectionCode;

    @Schema(description = "Day of week represented as ISO-8601 number (1=Monday)")
    private Integer dayOfWeek;

    @Schema(description = "Scheduled start time in HH:mm format", example = "09:00")
    private String startTime;

    @Schema(description = "Scheduled end time in HH:mm format", example = "10:30")
    private String endTime;

    @Schema(description = "Classroom or online location details")
    private String location;

    @Schema(description = "Maximum number of students allowed")
    private Integer maxStudents;

    @Schema(description = "Minutes after scheduled start before students are marked late", example = "15")
    private Integer lateThresholdMinutes;

    @JsonProperty("student_ids")
    @JsonAlias("studentIds")
    @Schema(description = "Optional list of students to enroll immediately in the new section")
    private List<UUID> studentIds;

    public UUID getCourseId() {
        return courseId;
    }

    public void setCourseId(UUID courseId) {
        this.courseId = courseId;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public void setProfessorId(UUID professorId) {
        this.professorId = professorId;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public Integer getLateThresholdMinutes() {
        return lateThresholdMinutes;
    }

    public void setLateThresholdMinutes(Integer lateThresholdMinutes) {
        this.lateThresholdMinutes = lateThresholdMinutes;
    }

    public List<UUID> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<UUID> studentIds) {
        this.studentIds = studentIds;
    }
}
