package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight projection of attendance data used by session queries.
 */
@Schema(name = "SessionAttendanceRecord", description = "Attendance record enriched with student details")
public class SessionAttendanceRecord {

    @Schema(description = "Identifier of the attendance record")
    private UUID id;

    @Schema(description = "Session identifier associated with the record")
    private UUID sessionId;

    @Schema(description = "Student identifier associated with the record")
    private UUID studentId;

    @Schema(description = "Identifier of the section hosting the attendance session")
    private UUID sectionId;

    @Schema(description = "Identifier of the parent course for the section")
    private UUID courseId;

    @Schema(description = "Code used to reference the section")
    private String sectionCode;

    @Schema(description = "Course code associated with the section")
    private String courseCode;

    @Schema(description = "Course title associated with the section")
    private String courseTitle;

    @Schema(description = "Attendance status value")
    private String status;

    @Schema(description = "Confidence score from the recognition system")
    private Double confidenceScore;

    @Schema(description = "Marking method used to capture the attendance record")
    private String markingMethod;

    @Schema(description = "Timestamp when attendance was marked")
    private OffsetDateTime markedAt;

    @Schema(description = "Last time the student was observed")
    private OffsetDateTime lastSeen;

    @Schema(description = "Instructor notes associated with the record")
    private String notes;

    @Schema(description = "Student details associated with the attendance record")
    private StudentDto student;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

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

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getMarkingMethod() {
        return markingMethod;
    }

    public void setMarkingMethod(String markingMethod) {
        this.markingMethod = markingMethod;
    }

    public OffsetDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(OffsetDateTime markedAt) {
        this.markedAt = markedAt;
    }

    public OffsetDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(OffsetDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public StudentDto getStudent() {
        return student;
    }

    public void setStudent(StudentDto student) {
        this.student = student;
    }
}
