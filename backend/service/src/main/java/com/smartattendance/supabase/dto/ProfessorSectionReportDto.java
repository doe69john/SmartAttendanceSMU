package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfessorSectionReport", description = "Aggregated metrics for a professor's section")
public class ProfessorSectionReportDto {

    @Schema(description = "Section identifier")
    private UUID sectionId;

    @Schema(description = "Course identifier")
    private UUID courseId;

    @Schema(description = "Course code")
    private String courseCode;

    @Schema(description = "Course title")
    private String courseTitle;

    @Schema(description = "Section code")
    private String sectionCode;

    @Schema(description = "Location information")
    private String location;

    @Schema(description = "Current enrolled students")
    private int enrolledStudents;

    @Schema(description = "Maximum capacity")
    private int maxStudents;

    @Schema(description = "Total scheduled sessions")
    private int totalSessions;

    @Schema(description = "Completed sessions")
    private int completedSessions;

    @Schema(description = "Upcoming sessions")
    private int upcomingSessions;

    @Schema(description = "Average attendance rate across completed sessions")
    private double averageAttendanceRate;

    @Schema(description = "Average present rate across completed sessions")
    private double averagePresentRate;

    @Schema(description = "Average late rate across completed sessions")
    private double averageLateRate;

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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getEnrolledStudents() {
        return enrolledStudents;
    }

    public void setEnrolledStudents(int enrolledStudents) {
        this.enrolledStudents = enrolledStudents;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(int maxStudents) {
        this.maxStudents = maxStudents;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(int completedSessions) {
        this.completedSessions = completedSessions;
    }

    public int getUpcomingSessions() {
        return upcomingSessions;
    }

    public void setUpcomingSessions(int upcomingSessions) {
        this.upcomingSessions = upcomingSessions;
    }

    public double getAverageAttendanceRate() {
        return averageAttendanceRate;
    }

    public void setAverageAttendanceRate(double averageAttendanceRate) {
        this.averageAttendanceRate = averageAttendanceRate;
    }

    public double getAveragePresentRate() {
        return averagePresentRate;
    }

    public void setAveragePresentRate(double averagePresentRate) {
        this.averagePresentRate = averagePresentRate;
    }

    public double getAverageLateRate() {
        return averageLateRate;
    }

    public void setAverageLateRate(double averageLateRate) {
        this.averageLateRate = averageLateRate;
    }
}
