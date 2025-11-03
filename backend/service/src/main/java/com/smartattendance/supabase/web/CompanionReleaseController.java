package com.smartattendance.supabase.web;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.smartattendance.supabase.dto.CompanionReleaseResponse;
import com.smartattendance.supabase.service.companion.CompanionReleaseService;
import com.smartattendance.supabase.service.companion.CompanionReleaseService.InstallerDownload;
import com.smartattendance.supabase.service.companion.CompanionReleaseService.InstallerPlatform;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/companion/releases")
@Tag(name = "Companion Releases", description = "Access metadata for native companion installers")
public class CompanionReleaseController {

    private final CompanionReleaseService releaseService;

    public CompanionReleaseController(CompanionReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/latest")
    @Operation(summary = "Latest companion release", description = "Returns metadata for the most recently published companion app build.")
    public ResponseEntity<CompanionReleaseResponse> getLatestRelease() {
        Optional<CompanionReleaseResponse> response = releaseService.fetchLatestRelease();
        response.ifPresent(this::injectDownloadEndpoints);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/latest/download/{platform}")
    @PreAuthorize("hasRole('PROFESSOR') or hasRole('ADMIN')")
    @Operation(summary = "Download companion installer", description = "Streams the latest companion installer for the requested platform.")
    public ResponseEntity<StreamingResponseBody> downloadLatestInstaller(@PathVariable("platform") String platformValue) {
        Optional<InstallerPlatform> platformOptional = InstallerPlatform.from(platformValue);
        if (platformOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Optional<InstallerDownload> downloadOptional = releaseService.downloadLatestInstaller(platformOptional.get());
        if (downloadOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        InstallerDownload download = downloadOptional.get();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.getContentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        if (download.getContentLength() >= 0) {
            headers.setContentLength(download.getContentLength());
        }

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(download.getFilename(), StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(contentDisposition);

        StreamingResponseBody body = outputStream -> {
            try (InstallerDownload stream = download) {
                stream.transferTo(outputStream);
            }
        };

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private void injectDownloadEndpoints(CompanionReleaseResponse response) {
        if (response == null) {
            return;
        }
        if (StringUtils.hasText(response.getMacPath())) {
            response.setMacUrl("/companion/releases/latest/download/mac");
        }
        if (StringUtils.hasText(response.getWindowsPath())) {
            response.setWindowsUrl("/companion/releases/latest/download/windows");
        }
    }
}
