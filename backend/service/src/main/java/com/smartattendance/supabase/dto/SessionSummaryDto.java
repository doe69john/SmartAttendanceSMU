package com.smartattendance.supabase.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionSummary", description = "Summary metrics for an attendance session")
public class SessionSummaryDto {

    @Schema(description = "Unique identifier of the session")
    private UUID id;

    @Schema(description = "Identifier of the section hosting the session")
    private UUID sectionId;

    @Schema(description = "Date the session occurs")
    private LocalDate sessionDate;

    @Schema(description = "Scheduled start timestamp")
    private OffsetDateTime startTime;

    @Schema(description = "Scheduled end timestamp")
    private OffsetDateTime endTime;

    @Schema(description = "Current lifecycle status of the session")
    private String status;

    @Schema(description = "Location where the session is held")
    private String location;

    @Schema(description = "Additional instructor notes")
    private String notes;

    @Schema(description = "Number of attendance records captured (any status)")
    private int attendanceCount;

    @Schema(description = "Total number of students expected for this session")
    private int totalStudents;

    @Schema(description = "Number of students with a present mark")
    private int presentCount;

    @Schema(description = "Number of students with a late mark")
    private int lateCount;

    @Schema(description = "Number of students with an absent mark")
    private int absentCount;

    @Schema(description = "Recorded student rows available for the session")
    private int recordedStudents;

    @Schema(description = "Day of week label for the session date")
    private String dayLabel;

    @Schema(description = "Formatted summary of the session's time range")
    private String timeRangeLabel;

    @Schema(description = "Formatted description of captured attendance compared to expected")
    private String attendanceSummary;

    @Schema(description = "Attendance rate for the session expressed as a decimal between 0 and 1")
    private double attendanceRate;

    @Schema(description = "Present rate for the session expressed as a decimal between 0 and 1")
    private double presentRate;

    @Schema(description = "Late rate for the session expressed as a decimal between 0 and 1")
    private double lateRate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = sectionId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getAttendanceCount() {
        return attendanceCount;
    }

    public void setAttendanceCount(int attendanceCount) {
        this.attendanceCount = attendanceCount;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public void setPresentCount(int presentCount) {
        this.presentCount = presentCount;
    }

    public int getLateCount() {
        return lateCount;
    }

    public void setLateCount(int lateCount) {
        this.lateCount = lateCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    public int getRecordedStudents() {
        return recordedStudents;
    }

    public void setRecordedStudents(int recordedStudents) {
        this.recordedStudents = recordedStudents;
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

    public String getAttendanceSummary() {
        return attendanceSummary;
    }

    public void setAttendanceSummary(String attendanceSummary) {
        this.attendanceSummary = attendanceSummary;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }

    public double getPresentRate() {
        return presentRate;
    }

    public void setPresentRate(double presentRate) {
        this.presentRate = presentRate;
    }

    public double getLateRate() {
        return lateRate;
    }

    public void setLateRate(double lateRate) {
        this.lateRate = lateRate;
    }
}
