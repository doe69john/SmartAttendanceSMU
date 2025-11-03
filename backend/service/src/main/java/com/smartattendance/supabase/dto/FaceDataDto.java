package com.smartattendance.supabase.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceData", description = "Metadata describing captured face data for a student")
public class FaceDataDto {

    @JsonProperty("id")
    @Schema(description = "Identifier of the face data record")
    private UUID id;

    @JsonProperty("student_id")
    @Schema(description = "Student identifier associated with the face data")
    private UUID studentId;

    @JsonProperty("image_url")
    @Schema(description = "Storage folder path that contains captured face images")
    private String imagePath;

    @JsonProperty("embedding_vector")
    @Schema(description = "Vector representation of the face, when available")
    private JsonNode embeddingVector;

    @JsonProperty("processing_status")
    @Schema(description = "Processing status within the recognition pipeline")
    private String processingStatus;

    @JsonProperty("face_encoding")
    @Schema(description = "Face encoding produced by recognition algorithms")
    private JsonNode faceEncoding;

    @JsonProperty("metadata")
    @Schema(description = "Additional metadata provided by upstream services")
    private JsonNode metadata;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the face data was created")
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public JsonNode getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(JsonNode embeddingVector) {
        this.embeddingVector = embeddingVector;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public JsonNode getFaceEncoding() {
        return faceEncoding;
    }

    public void setFaceEncoding(JsonNode faceEncoding) {
        this.faceEncoding = faceEncoding;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
