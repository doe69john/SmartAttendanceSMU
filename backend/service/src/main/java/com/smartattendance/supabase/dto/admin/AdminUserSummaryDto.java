package com.smartattendance.supabase.dto.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminUserSummary", description = "Summary information about a user for administrative management")
public class AdminUserSummaryDto {

    private UUID id;
    private UUID userId;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String staffId;
    private String studentId;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Schema(description = "Number of stored face data records associated with the student")
    private Integer faceDataCount;
    @Schema(description = "True if the student currently has stored face data")
    private Boolean hasFaceData;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getFaceDataCount() {
        return faceDataCount;
    }

    public void setFaceDataCount(Integer faceDataCount) {
        this.faceDataCount = faceDataCount;
    }

    public Boolean getHasFaceData() {
        return hasFaceData;
    }

    public void setHasFaceData(Boolean hasFaceData) {
        this.hasFaceData = hasFaceData;
    }
}
