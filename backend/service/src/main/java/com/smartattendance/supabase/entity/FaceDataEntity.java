package com.smartattendance.supabase.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "face_data")
public class FaceDataEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "student_id", nullable = false, columnDefinition = "uuid")
    private UUID studentId;

    @Column(name = "image_url", nullable = false)
    private String imagePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding_vector", columnDefinition = "jsonb")
    private JsonNode embeddingVector;

    @Column(name = "processing_status")
    private String processingStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "face_encoding", columnDefinition = "jsonb")
    private JsonNode faceEncoding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "created_at")
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
