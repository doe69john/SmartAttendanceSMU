package com.smartattendance.supabase.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.FaceDataCreateRequest;
import com.smartattendance.supabase.dto.FaceDataDeleteRequest;
import com.smartattendance.supabase.dto.FaceDataDto;
import com.smartattendance.supabase.dto.FaceDataStatusResponse;
import com.smartattendance.supabase.service.face.FaceDataAdminService;
import com.smartattendance.supabase.web.support.StudentOwnershipGuard;

@RestController
@RequestMapping("/api/face-data")
@Tag(name = "Face Data", description = "Administrative operations for stored biometric samples")
public class FaceDataAdminController {

    private final FaceDataAdminService faceDataAdminService;
    private final StudentOwnershipGuard studentOwnershipGuard;

    public FaceDataAdminController(FaceDataAdminService faceDataAdminService,
                                   StudentOwnershipGuard studentOwnershipGuard) {
        this.faceDataAdminService = faceDataAdminService;
        this.studentOwnershipGuard = studentOwnershipGuard;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR')")
    @Operation(summary = "List face data", description = "Returns stored face data records filtered by student when provided.")
    public List<FaceDataDto> listFaceData(@RequestParam(name = "studentId", required = false) UUID userId) {
        return faceDataAdminService.listFaceData(userId);
    }

    @GetMapping("/{studentId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "Get face data status", description = "Provides a summary of biometric data availability for a student.")
    public FaceDataStatusResponse status(@PathVariable("studentId") UUID userId,
                                         Authentication authentication) {
        studentOwnershipGuard.requireOwnership(authentication, userId,
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required"),
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to view status for this student"));
        return faceDataAdminService.getStatus(userId);
    }

    @PostMapping("/{studentId}/images")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "Create face data", description = "Registers captured face data for a student and returns the persisted record.")
    public ResponseEntity<FaceDataDto> createFaceData(@PathVariable("studentId") UUID userId,
                                                      Authentication authentication,
                                                      @RequestBody FaceDataCreateRequest request) {
        studentOwnershipGuard.requireOwnership(authentication, userId,
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required"),
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to create face data for this student"));
        FaceDataDto dto = faceDataAdminService.createFaceData(userId, request);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "Delete face data", description = "Removes stored face data for one or more students.")
    public ResponseEntity<Void> deleteFaceData(@RequestBody(required = false) FaceDataDeleteRequest request,
                                               @RequestParam(name = "studentId", required = false) UUID userId,
                                               Authentication authentication) {
        if (userId != null) {
            studentOwnershipGuard.requireOwnership(authentication, userId,
                    () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required"),
                    () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Not authorized to delete face data for this student"));
        }
        faceDataAdminService.deleteByRequest(request, userId);
        return ResponseEntity.noContent().build();
    }
}
