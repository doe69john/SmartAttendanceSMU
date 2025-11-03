package com.smartattendance.supabase.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.core.io.buffer.DataBuffer;

import com.smartattendance.supabase.dto.FaceImageFileDto;
import com.smartattendance.supabase.dto.FaceImageUploadResponse;
import com.smartattendance.supabase.dto.StorageDownload;
import com.smartattendance.supabase.service.face.FaceImageStorageService;
import com.smartattendance.vision.preprocess.FaceImageProcessingOptions;
import com.smartattendance.supabase.web.support.StudentOwnershipGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/storage/face-images")
@Tag(name = "Face Image Storage", description = "Upload, list, download, and delete face image files")
public class FaceImageStorageController {

    private final FaceImageStorageService storageService;
    private final StudentOwnershipGuard studentOwnershipGuard;

    public FaceImageStorageController(FaceImageStorageService storageService,
                                      StudentOwnershipGuard studentOwnershipGuard) {
        this.storageService = storageService;
        this.studentOwnershipGuard = studentOwnershipGuard;
    }

    @PostMapping(value = "/{studentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "Upload face image", description = "Uploads a face image for a student with optional upsert semantics.")
    public ResponseEntity<FaceImageUploadResponse> upload(@PathVariable UUID studentId,
                                                          @RequestPart("file") MultipartFile file,
                                                          @RequestParam(name = "upsert", defaultValue = "false") boolean upsert,
                                                          @RequestParam(name = "frameWidth", required = false) Integer frameWidth,
                                                          @RequestParam(name = "frameHeight", required = false) Integer frameHeight,
                                                          @RequestParam(name = "bboxX", required = false) Integer bboxX,
                                                          @RequestParam(name = "bboxY", required = false) Integer bboxY,
                                                          @RequestParam(name = "bboxWidth", required = false) Integer bboxWidth,
                                                          @RequestParam(name = "bboxHeight", required = false) Integer bboxHeight,
                                                          Authentication authentication) {
        studentOwnershipGuard.requireOwnership(authentication, studentId,
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "student_id is required"),
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to manage this student's face images"));
        FaceImageProcessingOptions options = FaceImageProcessingOptions.of(
                frameWidth,
                frameHeight,
                bboxX,
                bboxY,
                bboxWidth,
                bboxHeight);
        FaceImageUploadResponse response = storageService.upload(studentId, file, upsert, options);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{studentId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "List face images", description = "Returns the metadata for all stored face image files for a student.")
    public List<FaceImageFileDto> list(@PathVariable UUID studentId, Authentication authentication) {
        studentOwnershipGuard.requireOwnership(authentication, studentId,
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "student_id is required"),
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to manage this student's face images"));
        return storageService.listStudentImages(studentId);
    }

    @GetMapping("/{studentId}/download")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "Download face image", description = "Streams a specific face image file for a student.")
    public ResponseEntity<Flux<DataBuffer>> download(@PathVariable UUID studentId,
                                                     @RequestParam("fileName") String fileName,
                                                     Authentication authentication) {
        studentOwnershipGuard.requireOwnership(authentication, studentId,
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "student_id is required"),
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to manage this student's face images"));
        StorageDownload download = storageService.download(studentId, fileName);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(download.getMediaType() != null ? download.getMediaType() : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        if (download.getContentLength() != null) {
            builder.contentLength(download.getContentLength());
        }
        return builder.body(download.getData());
    }

    @DeleteMapping("/{studentId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROFESSOR') or hasRole('STUDENT')")
    @Operation(summary = "Delete face image(s)", description = "Deletes one or all stored face image files for a student.")
    public ResponseEntity<Void> delete(@PathVariable UUID studentId,
                                       @RequestParam(name = "fileName", required = false) String fileName,
                                       Authentication authentication) {
        studentOwnershipGuard.requireOwnership(authentication, studentId,
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "student_id is required"),
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to manage this student's face images"));
        storageService.deleteImages(studentId, fileName);
        return ResponseEntity.noContent().build();
    }
}
