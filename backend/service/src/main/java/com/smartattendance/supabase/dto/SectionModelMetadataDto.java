package com.smartattendance.supabase.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionModelMetadata", description = "Details about the most recent section model retraining run")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionModelMetadataDto {

    @Schema(description = "Storage prefix where the trained model artifacts are stored")
    private String storagePrefix;

    @Schema(description = "Total number of face images used during retraining")
    private Long imageCount;

    @Schema(description = "Students enrolled in the section without any face captures")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<UUID> missingStudentIds;

    @Schema(description = "Relative API path to download the LBPH model artifact")
    private String modelDownloadPath;

    @Schema(description = "Relative API path to download the labels artifact")
    private String labelsDownloadPath;

    @Schema(description = "Relative API path to download the cascade classifier")
    private String cascadeDownloadPath;

    @Schema(description = "Mapping of student identifiers to friendly display names for the companion UI")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<UUID, String> labelDisplayNames;

    public String getStoragePrefix() {
        return storagePrefix;
    }

    public void setStoragePrefix(String storagePrefix) {
        this.storagePrefix = storagePrefix;
    }

    public Long getImageCount() {
        return imageCount;
    }

    public void setImageCount(Long imageCount) {
        this.imageCount = imageCount;
    }

    public List<UUID> getMissingStudentIds() {
        return missingStudentIds;
    }

    public void setMissingStudentIds(List<UUID> missingStudentIds) {
        this.missingStudentIds = missingStudentIds == null ? null : List.copyOf(missingStudentIds);
    }

    public String getModelDownloadPath() {
        return modelDownloadPath;
    }

    public void setModelDownloadPath(String modelDownloadPath) {
        this.modelDownloadPath = modelDownloadPath;
    }

    public String getLabelsDownloadPath() {
        return labelsDownloadPath;
    }

    public void setLabelsDownloadPath(String labelsDownloadPath) {
        this.labelsDownloadPath = labelsDownloadPath;
    }

    public String getCascadeDownloadPath() {
        return cascadeDownloadPath;
    }

    public void setCascadeDownloadPath(String cascadeDownloadPath) {
        this.cascadeDownloadPath = cascadeDownloadPath;
    }

    public Map<UUID, String> getLabelDisplayNames() {
        return labelDisplayNames;
    }

    public void setLabelDisplayNames(Map<UUID, String> labelDisplayNames) {
        this.labelDisplayNames = labelDisplayNames == null ? null : Map.copyOf(labelDisplayNames);
    }
}
