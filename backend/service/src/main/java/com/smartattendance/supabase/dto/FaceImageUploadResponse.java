package com.smartattendance.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceImageUploadResponse", description = "Response metadata returned after uploading a face image")
public class FaceImageUploadResponse {

    @JsonProperty("file_name")
    @Schema(description = "Name of the stored file")
    private String fileName;

    @JsonProperty("storage_path")
    @Schema(description = "Path in storage where the file resides")
    private String storagePath;

    @JsonProperty("public_url")
    @Schema(description = "Publicly accessible URL, when available")
    private String publicUrl;

    @JsonProperty("download_url")
    @Schema(description = "Pre-signed download URL, when available")
    private String downloadUrl;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
