package com.smartattendance.supabase.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Student", description = "Minimal representation of a student profile")
public class StudentDto {

    @Schema(description = "Unique identifier of the student")
    private UUID id;

    @Schema(description = "Institution-issued student number")
    private String studentNumber;

    @Schema(description = "Display name of the student")
    private String fullName;

    @Schema(description = "URL pointing to the student's avatar image")
    private String avatarUrl;

    @Schema(description = "Primary email address associated with the student")
    private String email;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void setStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
