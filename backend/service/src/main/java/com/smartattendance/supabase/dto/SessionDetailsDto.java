package com.smartattendance.supabase.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionDetails", description = "Detailed representation of an attendance session")
public class SessionDetailsDto {

    @Schema(description = "Unique identifier of the session")
    private UUID id;

    @Schema(description = "Identifier of the section containing the session")
    private UUID sectionId;

    @Schema(description = "Identifier of the professor supervising the session")
    private UUID professorId;

    @Schema(description = "Date of the session")
    private LocalDate sessionDate;

    @Schema(description = "Planned start time with timezone")
    private OffsetDateTime startTime;

    @Schema(description = "Planned end time with timezone")
    private OffsetDateTime endTime;

    @Schema(description = "Minutes after start when a student is considered late")
    private Integer lateThresholdMinutes;

    @Schema(description = "Lifecycle status")
    private String status;

    @Schema(description = "Location for the session")
    private String location;

    @Schema(description = "Instructor notes")
    private String notes;

    @Schema(description = "Metadata returned by the latest section model retraining")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SectionModelMetadataDto modelMetadata;

    @Schema(description = "Absolute backend API base URL the companion should call")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String backendBaseUrl;

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

    public UUID getProfessorId() {
        return professorId;
    }

    public void setProfessorId(UUID professorId) {
        this.professorId = professorId;
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

    public Integer getLateThresholdMinutes() {
        return lateThresholdMinutes;
    }

    public void setLateThresholdMinutes(Integer lateThresholdMinutes) {
        this.lateThresholdMinutes = lateThresholdMinutes;
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

    public SectionModelMetadataDto getModelMetadata() {
        return modelMetadata;
    }

    public void setModelMetadata(SectionModelMetadataDto modelMetadata) {
        this.modelMetadata = modelMetadata;
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl;
    }
}
