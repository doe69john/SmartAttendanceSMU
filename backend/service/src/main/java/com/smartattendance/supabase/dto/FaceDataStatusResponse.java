package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceDataStatus", description = "Summary status of available face data for a student")
public class FaceDataStatusResponse {

    @JsonProperty("has_face_data")
    @Schema(description = "Whether any face data exists for the student")
    private boolean hasFaceData;

    @JsonProperty("image_count")
    @Schema(description = "Number of stored images")
    private int imageCount;

    @JsonProperty("latest_status")
    @Schema(description = "Most recent processing status")
    private String latestStatus;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when status was last updated")
    private OffsetDateTime updatedAt;

    public boolean isHasFaceData() {
        return hasFaceData;
    }

    public void setHasFaceData(boolean hasFaceData) {
        this.hasFaceData = hasFaceData;
    }

    public int getImageCount() {
        return imageCount;
    }

    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    public String getLatestStatus() {
        return latestStatus;
    }

    public void setLatestStatus(String latestStatus) {
        this.latestStatus = latestStatus;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
