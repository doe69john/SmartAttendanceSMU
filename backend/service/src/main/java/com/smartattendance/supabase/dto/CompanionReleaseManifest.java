package com.smartattendance.supabase.dto;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CompanionReleaseManifest {

    private String version;

    private String notes;

    @JsonProperty("publishedAt")
    private String publishedAt;

    @JsonProperty("installers")
    private Map<String, CompanionReleaseInstaller> installers = Collections.emptyMap();

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

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Map<String, CompanionReleaseInstaller> getInstallers() {
        return installers;
    }

    public void setInstallers(Map<String, CompanionReleaseInstaller> installers) {
        this.installers = installers != null ? installers : Collections.emptyMap();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompanionReleaseInstaller {

        private String url;

        private String path;

        private String checksum;

        private Long size;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getChecksum() {
            return checksum;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }
    }
}
