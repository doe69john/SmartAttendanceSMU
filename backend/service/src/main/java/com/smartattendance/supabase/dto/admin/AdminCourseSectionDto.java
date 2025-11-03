package com.smartattendance.supabase.dto.admin;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminCourseSection", description = "Summary of a section within a course for administrators")
public class AdminCourseSectionDto {

    @Schema(description = "Unique identifier of the section", format = "uuid")
    private UUID sectionId;

    @Schema(description = "Code used to identify the section")
    private String sectionCode;

    @Schema(description = "Day of week the section is scheduled on (1 = Monday)")
    private Integer dayOfWeek;

    @Schema(description = "Scheduled start time for the section", format = "time")
    private String startTime;

    @Schema(description = "Scheduled end time for the section", format = "time")
    private String endTime;

    @Schema(description = "Location where the section meets")
    private String location;

    @Schema(description = "Whether the section is active")
    private boolean active;

    @Schema(description = "Identifier of the professor assigned to the section", format = "uuid")
    private UUID professorId;

    @Schema(description = "Display name of the assigned professor")
    private String professorName;

    @Schema(description = "Email address of the assigned professor")
    private String professorEmail;

    @Schema(description = "Number of active students enrolled in the section")
    private int studentCount;

    @Schema(description = "Number of sessions scheduled for the section")
    private int sessionCount;

    @Schema(description = "Average attendance rate across recorded sessions")
    private Double attendanceRate;

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public int getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(int studentCount) {
        this.studentCount = studentCount;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public Double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(Double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }
}
