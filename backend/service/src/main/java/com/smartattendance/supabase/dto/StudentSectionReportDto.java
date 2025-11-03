package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentSectionReport", description = "Attendance summary for a student within a specific section")
public class StudentSectionReportDto {

    @Schema(description = "Identifier of the section")
    private UUID sectionId;

    @Schema(description = "Identifier of the course associated with the section")
    private UUID courseId;

    @Schema(description = "Course code for the associated section")
    private String courseCode;

    @Schema(description = "Course title for the associated section")
    private String courseTitle;

    @Schema(description = "Section code")
    private String sectionCode;

    @Schema(description = "Total sessions scheduled for the section")
    private int totalSessions;

    @Schema(description = "Sessions attended (present or late)")
    private int attendedSessions;

    @Schema(description = "Sessions recorded as absent")
    private int missedSessions;

    @Schema(description = "Sessions with an attendance record")
    private int recordedSessions;

    @Schema(description = "Attendance rate for the student within the section")
    private double attendanceRate;

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

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public int getAttendedSessions() {
        return attendedSessions;
    }

    public void setAttendedSessions(int attendedSessions) {
        this.attendedSessions = attendedSessions;
    }

    public int getMissedSessions() {
        return missedSessions;
    }

    public void setMissedSessions(int missedSessions) {
        this.missedSessions = missedSessions;
    }

    public int getRecordedSessions() {
        return recordedSessions;
    }

    public void setRecordedSessions(int recordedSessions) {
        this.recordedSessions = recordedSessions;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }
}
