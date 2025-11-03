package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompanionRelease", description = "Metadata describing a native companion app release")
public class CompanionReleaseResponse {

    @Schema(description = "Semantic version for the release", example = "1.2.0")
    private String version;

    @Schema(description = "Optional release notes to highlight changes")
    private String notes;

    @Schema(description = "Download URL for the macOS installer")
    private String macUrl;

    @Schema(description = "Relative storage path for the macOS installer")
    private String macPath;

    @Schema(description = "Download URL for the Windows installer")
    private String windowsUrl;

    @Schema(description = "Relative storage path for the Windows installer")
    private String windowsPath;

    @Schema(description = "Name of the Supabase Storage bucket containing the installers")
    private String bucket;

    @Schema(description = "Base URL used to download public storage objects")
    private String publicBaseUrl;

    @Schema(description = "ISO-8601 timestamp for when the release was published")
    private String publishedAt;

    public CompanionReleaseResponse() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getMacUrl() {
        return macUrl;
    }

    public void setMacUrl(String macUrl) {
        this.macUrl = macUrl;
    }

    public String getMacPath() {
        return macPath;
    }

    public void setMacPath(String macPath) {
        this.macPath = macPath;
    }

    public String getWindowsUrl() {
        return windowsUrl;
    }

    public void setWindowsUrl(String windowsUrl) {
        this.windowsUrl = windowsUrl;
    }

    public String getWindowsPath() {
        return windowsPath;
    }

    public void setWindowsPath(String windowsPath) {
        this.windowsPath = windowsPath;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
