package com.smartattendance.supabase.dto.admin;

import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminSectionSummary", description = "Administrative view of a teaching section including professor assignment")
public class AdminSectionSummaryDto {

    @Schema(description = "Unique identifier of the section")
    private UUID sectionId;

    @Schema(description = "Identifier of the parent course")
    private UUID courseId;

    @Schema(description = "Course code associated with the section")
    private String courseCode;

    @Schema(description = "Course title associated with the section")
    private String courseTitle;

    @Schema(description = "Code used to reference the section")
    private String sectionCode;

    @Schema(description = "Day of week represented as ISO-8601 number (1=Monday)")
    private Integer dayOfWeek;

    @Schema(description = "Human readable label for the scheduled meeting day")
    private String dayLabel;

    @Schema(description = "Formatted summary of the time range for the meeting")
    private String timeRangeLabel;

    @Schema(description = "Scheduled start time")
    private LocalTime startTime;

    @Schema(description = "Scheduled end time")
    private LocalTime endTime;

    @Schema(description = "Classroom or online location details")
    private String location;

    @Schema(description = "Maximum number of students allowed")
    private Integer maxStudents;

    @Schema(description = "Current number of enrolled students")
    private Integer enrolledCount;

    @Schema(description = "Formatted enrollment summary including capacity when available")
    private String enrollmentSummary;

    @Schema(description = "Minutes after scheduled start before students are marked late")
    private Integer lateThresholdMinutes;

    @Schema(description = "Identifier of the professor assigned to the section")
    private UUID professorId;

    @Schema(description = "Full name of the professor assigned to the section")
    private String professorName;

    @Schema(description = "Email of the professor assigned to the section")
    private String professorEmail;

    @Schema(description = "Staff identifier of the professor assigned to the section")
    private String professorStaffId;

    public UUID getSectionId() {
        return sectionId;
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = sectionId;
    }

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

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getDayLabel() {
        return dayLabel;
    }

    public void setDayLabel(String dayLabel) {
        this.dayLabel = dayLabel;
    }

    public String getTimeRangeLabel() {
        return timeRangeLabel;
    }

    public void setTimeRangeLabel(String timeRangeLabel) {
        this.timeRangeLabel = timeRangeLabel;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
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

    public Integer getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(Integer enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    public String getEnrollmentSummary() {
        return enrollmentSummary;
    }

    public void setEnrollmentSummary(String enrollmentSummary) {
        this.enrollmentSummary = enrollmentSummary;
    }

    public Integer getLateThresholdMinutes() {
        return lateThresholdMinutes;
    }

    public void setLateThresholdMinutes(Integer lateThresholdMinutes) {
        this.lateThresholdMinutes = lateThresholdMinutes;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public void setProfessorId(UUID professorId) {
        this.professorId = professorId;
    }

    public String getProfessorName() {
        return professorName;
    }

    public void setProfessorName(String professorName) {
        this.professorName = professorName;
    }

    public String getProfessorEmail() {
        return professorEmail;
    }

    public void setProfessorEmail(String professorEmail) {
        this.professorEmail = professorEmail;
    }

    public String getProfessorStaffId() {
        return professorStaffId;
    }

    public void setProfessorStaffId(String professorStaffId) {
        this.professorStaffId = professorStaffId;
    }
}
