package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "FaceCaptureAnalysisRequest", description = "Frame submitted for face capture pre-validation")
public class FaceCaptureAnalysisRequest {

    @NotBlank
    @Schema(description = "Base64 encoded frame data captured from the camera", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageData;

    public String getImageData() {
        return imageData;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }
}
