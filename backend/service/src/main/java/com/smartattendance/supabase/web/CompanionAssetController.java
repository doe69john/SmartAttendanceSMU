package com.smartattendance.supabase.web;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartattendance.supabase.service.recognition.SectionModelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/companion")
@Tag(name = "Companion Assets", description = "Download assets required by the native companion app")
public class CompanionAssetController {

    private static final String CASCADE_FILENAME = "haarcascade_frontalface_default.xml";

    private static final Logger log = LoggerFactory.getLogger(CompanionAssetController.class);

    private final SectionModelService sectionModelService;
    private final Resource cascadeResource;

    public CompanionAssetController(SectionModelService sectionModelService, ResourceLoader resourceLoader) {
        this.sectionModelService = sectionModelService;
        this.cascadeResource = resourceLoader.getResource("classpath:vision/models/" + CASCADE_FILENAME);
    }

    @GetMapping("/assets/cascade")
    @Operation(summary = "Download cascade classifier", description = "Provides the Haar cascade classifier required by the companion app.")
    public ResponseEntity<Resource> downloadCascade() {
        if (cascadeResource == null || !cascadeResource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(CASCADE_FILENAME))
                .body(cascadeResource);
    }

    @GetMapping("/sections/{sectionId}/models/lbph")
    @Operation(summary = "Download section LBPH model", description = "Streams the LBPH model for the requested section.")
    public ResponseEntity<Resource> downloadLbphModel(@PathVariable UUID sectionId) {
        log.info("Companion requested LBPH model for section {}", sectionId);
        return downloadArtifact(sectionId,
                "lbph.yml",
                MediaType.APPLICATION_OCTET_STREAM,
                "lbph.zip",
                true);
    }

    @GetMapping("/sections/{sectionId}/models/labels")
    @Operation(summary = "Download section labels", description = "Streams the labels file associated with the section model.")
    public ResponseEntity<Resource> downloadLabels(@PathVariable UUID sectionId) {
        log.info("Companion requested labels for section {}", sectionId);
        return downloadArtifact(sectionId, "labels.txt", MediaType.TEXT_PLAIN, "labels.txt");
    }

    private ResponseEntity<Resource> downloadArtifact(UUID sectionId,
                                                      String artifactName,
                                                      MediaType mediaType,
                                                      String filename) {
        return downloadArtifact(sectionId, artifactName, mediaType, filename, false);
    }

    private ResponseEntity<Resource> downloadArtifact(UUID sectionId,
                                                      String artifactName,
                                                      MediaType mediaType,
                                                      String filename,
                                                      boolean compressed) {
        log.info("Attempting to stream artifact '{}' for section {}", artifactName, sectionId);
        byte[] data = compressed
                ? sectionModelService.fetchCompressedModelArtifact(sectionId, artifactName)
                : sectionModelService.fetchModelArtifact(sectionId, artifactName);
        if (data == null || data.length == 0) {
            log.warn("Artifact '{}' for section {} is unavailable or empty", artifactName, sectionId);
            return ResponseEntity.notFound().build();
        }
        log.info("Serving artifact '{}' for section {} ({} bytes, compressed={})",
                artifactName,
                sectionId,
                data.length,
                compressed);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentLength(data.length)
                .body(resource);
    }

    private String contentDisposition(String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return disposition.toString();
    }
}

