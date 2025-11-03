package com.smartattendance.supabase.web;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.smartattendance.supabase.dto.FaceCaptureAnalysisRequest;
import com.smartattendance.supabase.dto.FaceCaptureAnalysisResponse;
import com.smartattendance.supabase.service.face.FaceCaptureAnalysisService;

@RestController
@Validated
@RequestMapping("/api/face-capture")
@Tag(name = "Face Capture", description = "Utilities for guiding the face enrollment flow")
public class FaceCaptureController {

    private final FaceCaptureAnalysisService analysisService;

    public FaceCaptureController(FaceCaptureAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Analyze capture frame", description = "Evaluates a camera frame to determine if exactly one face is present.")
    public ResponseEntity<FaceCaptureAnalysisResponse> analyze(@Valid @RequestBody FaceCaptureAnalysisRequest request) {
        FaceCaptureAnalysisResponse response = analysisService.analyse(request);
        return ResponseEntity.ok(response);
    }
}
