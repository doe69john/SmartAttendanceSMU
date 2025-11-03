package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StorageObject", description = "Metadata for objects stored in Supabase buckets")
public class StorageObjectDto {

    @Schema(description = "Unique identifier assigned by Supabase")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Object name within the bucket")
    private String name;

    @JsonProperty("bucket_id")
    @Schema(description = "Bucket identifier containing the object")
    private String bucketId;

    @JsonProperty("owner")
    @Schema(description = "Owner identifier if tracked")
    private String owner;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when the object was last updated")
    private OffsetDateTime updatedAt;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the object was created")
    private OffsetDateTime createdAt;

    @JsonProperty("last_accessed_at")
    @Schema(description = "Timestamp when the object was last accessed")
    private OffsetDateTime lastAccessedAt;

    @JsonProperty("size")
    @Schema(description = "Object size in bytes")
    private Long size;

    @JsonProperty("metadata")
    @Schema(description = "Arbitrary metadata stored with the object")
    private Map<String, Object> metadata;

    /** Full storage key such as {@code face-images/student/file.jpg}. */
    @Schema(description = "Full storage key for the object")
    private String key;

    /** Convenience field containing the relative path (prefix + name). */
    @Schema(description = "Relative path for easy linking")
    private String path;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(OffsetDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
