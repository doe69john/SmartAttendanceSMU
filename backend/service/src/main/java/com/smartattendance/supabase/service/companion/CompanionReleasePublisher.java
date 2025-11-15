package com.smartattendance.supabase.service.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartattendance.supabase.config.SupabaseStorageProperties;
import com.smartattendance.supabase.dto.StorageUploadResult;
import com.smartattendance.supabase.service.supabase.SupabaseStorageService;

@Component
public class CompanionReleasePublisher {

    private static final Logger logger = LoggerFactory.getLogger(CompanionReleasePublisher.class);
    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");
    private static final String STORAGE_BRANCH_PREFIX = "deploy";
    private static final String STORAGE_RELEASE_PREFIX = STORAGE_BRANCH_PREFIX + "/releases";
    private static final String LATEST_MANIFEST_PATH = STORAGE_RELEASE_PREFIX + "/latest.json";

    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final CompanionInstallerBuilder installerBuilder;
    private final boolean autoPublish;
    private final String releaseNotes;

    public CompanionReleasePublisher(SupabaseStorageService storageService,
                                     SupabaseStorageProperties storageProperties,
                                     ObjectMapper objectMapper,
                                     @Value("${companion.release.auto-publish:true}") boolean autoPublish,
                                     @Value("${companion.release.notes:Automated companion build}") String releaseNotes) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
        this.autoPublish = autoPublish;
        this.releaseNotes = releaseNotes;
        this.installerBuilder = new CompanionInstallerBuilder(detectBackendDirectory());
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void publishOnStartup() {
        if (!autoPublish) {
            logger.info("Companion auto-publish disabled. Skipping installer build.");
            return;
        }
        if (!storageService.isEnabled()) {
            logger.warn("Supabase storage disabled. Unable to publish companion installers: {}", storageService.getDisabledReason());
            return;
        }
        String bucket = storageProperties.getCompanionInstallerBucket();
        if (!StringUtils.hasText(bucket)) {
            logger.warn("Companion installer bucket not configured. Set supabase.storage.companion-installer-bucket to enable auto-publish.");
            return;
        }
        String serviceKey = storageProperties.getServiceAccessToken();
        if (!StringUtils.hasText(serviceKey)) {
            logger.warn("Companion auto-publish requires supabase.storage.service-access-token to provide a Supabase service "
                    + "role key. Populate SUPABASE_SERVICE_ROLE_KEY in .env.local or configure a dedicated service user JWT "
                    + "for automated uploads.");
            return;
        }
        try {
            storageService.withServiceKey(serviceKey, () -> {
                try {
                    CompanionInstallerBuilder.BuildArtifacts artifacts = installerBuilder.buildInstallers();
                    String basePath = STORAGE_RELEASE_PREFIX + "/" + artifacts.version();
                    String macObject = basePath + "/mac/" + artifacts.macArchive().getFileName();
                    String windowsObject = basePath + "/windows/" + artifacts.windowsArchive().getFileName();

                    upload(bucket, macObject, artifacts.macArchive());
                    upload(bucket, windowsObject, artifacts.windowsArchive());

                    Map<String, Object> manifest = createManifest(artifacts, macObject, windowsObject);
                    byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
                    String manifestPath = basePath + "/manifest.json";
                    storageService.upload(bucket, manifestPath, MediaType.APPLICATION_JSON, manifestBytes, true);
                    storageService.upload(bucket, LATEST_MANIFEST_PATH, MediaType.APPLICATION_JSON, manifestBytes, true);
                    logger.info("Published companion installers to bucket '{}' under version {}", bucket, artifacts.version());
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to publish companion installers: " + ex.getMessage(), ex);
                }
                return null;
            });
        } catch (Exception ex) {
            logger.error("Failed to auto-publish companion installers: {}", ex.getMessage(), ex);
        }
    }

    private void upload(String bucket, String objectPath, Path file) throws IOException {
        StorageUploadResult result = storageService.uploadFile(bucket, objectPath, ZIP_MEDIA_TYPE, file, true);
        if (logger.isDebugEnabled()) {
            logger.debug("Uploaded {} (key={})", objectPath, result != null ? result.getKey() : "n/a");
        }
    }

    private Map<String, Object> createManifest(CompanionInstallerBuilder.BuildArtifacts artifacts,
                                               String macObject,
                                               String windowsObject) {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("version", artifacts.version());
        manifest.put("notes", releaseNotes);
        Instant publishedAt = artifacts.publishedAt();
        manifest.put("publishedAt", publishedAt != null ? publishedAt.toString() : Instant.now().toString());
        Map<String, Object> installers = new HashMap<>();
        installers.put("mac", Map.of(
                "path", macObject,
                "checksum", artifacts.macChecksum(),
                "size", artifacts.macSize()
        ));
        installers.put("windows", Map.of(
                "path", windowsObject,
                "checksum", artifacts.windowsChecksum(),
                "size", artifacts.windowsSize()
        ));
        manifest.put("installers", installers);
        return manifest;
    }

    private Path detectBackendDirectory() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("pom.xml")) && Files.isDirectory(cwd.resolve("src"))) {
            return cwd;
        }
        Path backendCandidate = cwd.resolve("backend");
        if (Files.exists(backendCandidate.resolve("pom.xml"))) {
            return backendCandidate;
        }
        Path parentBackend = cwd.getParent() != null ? cwd.getParent().resolve("backend") : null;
        if (parentBackend != null && Files.exists(parentBackend.resolve("pom.xml"))) {
            return parentBackend;
        }
        logger.warn("Unable to confidently detect backend directory from {}. Using current directory.", cwd);
        return cwd;
    }
}
