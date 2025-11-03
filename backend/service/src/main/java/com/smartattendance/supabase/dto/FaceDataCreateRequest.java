package com.smartattendance.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceDataCreateRequest", description = "Payload for creating face data records")
public class FaceDataCreateRequest {

    @JsonProperty("image_url")
    @Schema(description = "Path to the storage folder that contains the uploaded face images")
    private String imagePath;

    @JsonProperty("processing_status")
    @Schema(description = "Processing status to persist with the record")
    private String processingStatus;

    @JsonProperty("embedding_vector")
    @Schema(description = "Optional embedding vector for the face")
    private JsonNode embeddingVector;

    @JsonProperty("face_encoding")
    @Schema(description = "Optional face encoding payload")
    private JsonNode faceEncoding;

    @JsonProperty("metadata")
    @Schema(description = "Additional metadata to persist with the face data")
    private JsonNode metadata;

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public JsonNode getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(JsonNode embeddingVector) {
        this.embeddingVector = embeddingVector;
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
}
