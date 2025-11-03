package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Profile", description = "Profile information for an authenticated user")
public class ProfileDto {

    @Schema(description = "Unique identifier of the profile")
    private UUID id;

    @Schema(description = "Identifier of the linked authentication user")
    private UUID userId;

    @Schema(description = "Email address associated with the profile")
    private String email;

    @Schema(description = "Full name of the user")
    private String fullName;

    @Schema(description = "Primary role of the user")
    private String role;

    @Schema(description = "Staff identifier, when applicable")
    private String staffId;

    @Schema(description = "Student identifier, when applicable")
    private String studentId;

    @Schema(description = "Avatar image URL")
    private String avatarUrl;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
