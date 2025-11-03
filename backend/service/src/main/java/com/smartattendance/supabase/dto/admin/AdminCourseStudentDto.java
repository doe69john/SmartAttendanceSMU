package com.smartattendance.supabase.dto.admin;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminCourseStudent", description = "Student enrollment summary for a course section")
public class AdminCourseStudentDto {

    @Schema(description = "Unique identifier of the student profile", format = "uuid")
    private UUID studentId;

    @Schema(description = "Display name of the student")
    private String fullName;

    @Schema(description = "Email address of the student")
    private String email;

    @Schema(description = "Institution-issued student number")
    private String studentNumber;

    @Schema(description = "Identifier of the section the student is enrolled in", format = "uuid")
    private UUID sectionId;

    @Schema(description = "Code of the enrolled section")
    private String sectionCode;

    @Schema(description = "Total sessions scheduled for the section")
    private int totalSessions;

    @Schema(description = "Sessions with recorded attendance for the student")
    private int recordedSessions;

    @Schema(description = "Sessions the student attended (present or late)")
    private int attendedSessions;

    @Schema(description = "Attendance rate computed from recorded sessions")
    private Double attendanceRate;

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = sectionId;
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

    public int getRecordedSessions() {
        return recordedSessions;
    }

    public void setRecordedSessions(int recordedSessions) {
        this.recordedSessions = recordedSessions;
    }

    public int getAttendedSessions() {
        return attendedSessions;
    }

    public void setAttendedSessions(int attendedSessions) {
        this.attendedSessions = attendedSessions;
    }

    public Double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(Double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }
}
