package com.smartattendance.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StorageUploadResult", description = "Raw storage upload response returned by Supabase")
public class StorageUploadResult {

    /** Full key reported by Supabase, e.g. {@code face-images/student/file.jpg}. */
    @JsonProperty("Key")
    @Schema(description = "Storage key generated for the uploaded object")
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
