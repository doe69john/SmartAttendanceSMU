package com.smartattendance.supabase.dto;

import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionSummary", description = "Aggregated data about a teaching section")
public class SectionSummaryDto {

    @Schema(description = "Unique identifier of the section")
    private UUID id;

    @Schema(description = "Identifier of the parent course")
    private UUID courseId;

    @Schema(description = "Course code associated with the section")
    private String courseCode;

    @Schema(description = "Course title associated with the section")
    private String courseTitle;

    @Schema(description = "Course description associated with the section")
    private String courseDescription;

    @Schema(description = "Code used to reference the section")
    private String sectionCode;

    @Schema(description = "Day of week represented as ISO-8601 number (1=Monday)")
    private int dayOfWeek;

    @Schema(description = "Scheduled start time")
    private LocalTime startTime;

    @Schema(description = "Scheduled end time")
    private LocalTime endTime;

    @Schema(description = "Location information for the section")
    private String location;

    @Schema(description = "Maximum number of students")
    private int maxStudents;

    @Schema(description = "Current number of enrolled students")
    private int enrolledCount;

    @Schema(description = "Human readable label for the scheduled meeting day")
    private String dayLabel;

    @Schema(description = "Formatted summary of the time range for the meeting")
    private String timeRangeLabel;

    @Schema(description = "Formatted enrollment summary including capacity when available")
    private String enrollmentSummary;

    @Schema(description = "Minutes after scheduled start when students are marked late")
    private Integer lateThresholdMinutes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getCourseDescription() {
        return courseDescription;
    }

    public void setCourseDescription(String courseDescription) {
        this.courseDescription = courseDescription;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
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

    public int getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(int maxStudents) {
        this.maxStudents = maxStudents;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
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
}
