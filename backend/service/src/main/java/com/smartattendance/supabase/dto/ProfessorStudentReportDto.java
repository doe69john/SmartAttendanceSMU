package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfessorStudentReport", description = "Aggregated attendance metrics for a student under a professor")
public class ProfessorStudentReportDto {

    @Schema(description = "Student profile details")
    private StudentDto student;

    @Schema(description = "Number of active sections shared with the professor")
    private int sectionCount;

    @Schema(description = "Number of distinct courses shared with the professor")
    private int courseCount;

    @Schema(description = "Total number of sessions scheduled across shared sections")
    private int totalSessions;

    @Schema(description = "Number of sessions where the student was marked present or late")
    private int attendedSessions;

    @Schema(description = "Number of sessions marked absent")
    private int missedSessions;

    @Schema(description = "Number of sessions with an attendance record")
    private int recordedSessions;

    @Schema(description = "Attendance rate computed from recorded sessions")
    private double attendanceRate;

    @Schema(description = "Timestamp of the most recent attendance record")
    private OffsetDateTime lastAttendanceAt;

    public StudentDto getStudent() {
        return student;
    }

    public void setStudent(StudentDto student) {
        this.student = student;
    }

    public int getSectionCount() {
        return sectionCount;
    }

    public void setSectionCount(int sectionCount) {
        this.sectionCount = sectionCount;
    }

    public int getCourseCount() {
        return courseCount;
    }

    public void setCourseCount(int courseCount) {
        this.courseCount = courseCount;
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

    public OffsetDateTime getLastAttendanceAt() {
        return lastAttendanceAt;
    }

    public void setLastAttendanceAt(OffsetDateTime lastAttendanceAt) {
        this.lastAttendanceAt = lastAttendanceAt;
    }
}
