package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceImageFile", description = "Metadata describing a stored face image file")
public class FaceImageFileDto {

    @JsonProperty("file_name")
    @Schema(description = "Name of the file in storage")
    private String fileName;

    @JsonProperty("size_bytes")
    @Schema(description = "Size of the file in bytes")
    private long sizeBytes;

    @JsonProperty("uploaded_at")
    @Schema(description = "Timestamp when the file was uploaded")
    private OffsetDateTime uploadedAt;

    @JsonProperty("storage_path")
    @Schema(description = "Storage path where the file resides")
    private String storagePath;

    @JsonProperty("download_url")
    @Schema(description = "Download URL for the stored file")
    private String downloadUrl;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(OffsetDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
