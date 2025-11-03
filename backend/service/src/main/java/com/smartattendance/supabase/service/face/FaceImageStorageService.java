package com.smartattendance.supabase.service.face;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.smartattendance.config.AttendanceProperties;
import com.smartattendance.supabase.config.SupabaseStorageProperties;
import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.dto.FaceImageFileDto;
import com.smartattendance.supabase.dto.FaceImageUploadResponse;
import com.smartattendance.supabase.dto.StorageDownload;
import com.smartattendance.supabase.dto.StorageObjectDto;
import com.smartattendance.supabase.dto.StorageUploadResult;
import com.smartattendance.supabase.repository.ProfileRepository;
import com.smartattendance.vision.HaarFaceDetector;
import com.smartattendance.vision.preprocess.FaceImageProcessingOptions;
import com.smartattendance.vision.preprocess.FaceImageProcessor;
import com.smartattendance.vision.preprocess.FaceImageProcessor.ProcessedImage;

import com.smartattendance.supabase.service.supabase.SupabaseStorageService;

@Service
public class FaceImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(FaceImageStorageService.class);

    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;
    private final ProfileRepository profileRepository;
    private final FaceImageProcessor faceImageProcessor;

    public FaceImageStorageService(SupabaseStorageService storageService,
                                   SupabaseStorageProperties storageProperties,
                                   ProfileRepository profileRepository,
                                   HaarFaceDetector haarFaceDetector,
                                   AttendanceProperties attendanceProperties) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.profileRepository = profileRepository;
        AttendanceProperties.Preprocessing preprocessing = attendanceProperties != null
                ? attendanceProperties.preprocessing()
                : new AttendanceProperties().preprocessing();
        this.faceImageProcessor = new FaceImageProcessor(preprocessing, haarFaceDetector);
        if (attendanceProperties != null) {
            attendanceProperties.onChange(snapshot ->
                    faceImageProcessor.updatePreprocessingConfig(snapshot.preprocessing()));
        }
    }

    public FaceImageUploadResponse upload(UUID studentId,
                                          MultipartFile file,
                                          boolean upsert,
                                          FaceImageProcessingOptions processingOptions) {
        assertStorageEnabled();
        if (studentId == null) {
            throw new IllegalArgumentException("student_id is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (fileName == null) {
            fileName = "capture-" + System.currentTimeMillis() + guessExtension(file.getContentType());
        }
        String objectPath = buildObjectPath(studentId, fileName);
        MediaType mediaType = resolveMediaType(file);

        StorageUploadResult result;
        try {
            byte[] data = file.getBytes();
            if (processingOptions != null) {
                try {
                    ProcessedImage processed = faceImageProcessor.process(data, processingOptions);
                    if (processed != null && processed.data() != null && processed.data().length > 0) {
                        data = processed.data();
                        if (processed.mediaType() != null) {
                            mediaType = processed.mediaType();
                        } else {
                            mediaType = MediaType.IMAGE_JPEG;
                        }
                    }
                } catch (RuntimeException ex) {
                    log.warn("Face image preprocessing failed; storing original frame: {}", ex.getMessage());
                }
            }
            result = storageService.upload(
                    storageProperties.getFaceImageBucket(),
                    objectPath,
                    mediaType,
                    data,
                    upsert);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read face image payload", ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to upload face image", ex);
        }

        String key = result != null && StringUtils.hasText(result.getKey())
                ? result.getKey()
                : storageProperties.getFaceImageBucket() + "/" + objectPath;

        FaceImageUploadResponse response = new FaceImageUploadResponse();
        response.setFileName(fileName);
        response.setStoragePath(objectPath);
        response.setPublicUrl(buildPublicUrl(objectPath));
        response.setDownloadUrl(buildDownloadUrl(studentId, fileName));
        if (!StringUtils.hasText(response.getPublicUrl())) {
            response.setPublicUrl(response.getDownloadUrl());
        }
        if (!StringUtils.hasText(response.getStoragePath())) {
            response.setStoragePath(key.substring(storageProperties.getFaceImageBucket().length() + 1));
        }
        return response;
    }

    public List<FaceImageFileDto> listStudentImages(UUID studentId) {
        assertStorageEnabled();
        if (studentId == null) {
            return List.of();
        }
        String primaryPrefix = resolveStoragePrefix(studentId) + "/";
        String legacyPrefix = studentId.toString() + "/";

        Map<String, StorageObjectDto> byPath = new LinkedHashMap<>();
        List<StorageObjectDto> primaryObjects = storageService.list(
                storageProperties.getFaceImageBucket(),
                primaryPrefix,
                1000,
                0,
                "name",
                "asc");
        if (primaryObjects != null) {
            for (StorageObjectDto object : primaryObjects) {
                if (object == null) {
                    continue;
                }
                String key = resolveObjectKey(primaryPrefix, object);
                byPath.put(key, object);
            }
        }
        if (!legacyPrefix.equals(primaryPrefix)) {
            List<StorageObjectDto> legacyObjects = storageService.list(
                    storageProperties.getFaceImageBucket(),
                    legacyPrefix,
                    1000,
                    0,
                    "name",
                    "asc");
            if (legacyObjects != null) {
                for (StorageObjectDto object : legacyObjects) {
                    if (object == null) {
                        continue;
                    }
                    String key = resolveObjectKey(legacyPrefix, object);
                    byPath.putIfAbsent(key, object);
                }
            }
        }

        List<FaceImageFileDto> results = new ArrayList<>();
        for (Map.Entry<String, StorageObjectDto> entry : byPath.entrySet()) {
            StorageObjectDto object = entry.getValue();
            if (!StringUtils.hasText(object.getName())) {
                continue;
            }
            FaceImageFileDto dto = new FaceImageFileDto();
            dto.setFileName(object.getName());
            dto.setSizeBytes(object.getSize() != null ? object.getSize() : 0L);
            dto.setUploadedAt(object.getUpdatedAt() != null ? object.getUpdatedAt() : object.getCreatedAt());
            dto.setStoragePath(entry.getKey());
            dto.setDownloadUrl(buildDownloadUrl(studentId, object.getName()));
            results.add(dto);
        }
        return results;
    }

    public void deleteImages(UUID studentId, String fileName) {
        assertStorageEnabled();
        if (studentId == null) {
            return;
        }
        String bucket = storageProperties.getFaceImageBucket();
        String primaryPrefix = resolveStoragePrefix(studentId) + "/";
        String legacyPrefix = studentId + "/";
        if (!StringUtils.hasText(fileName)) {
            deleteByPrefix(bucket, primaryPrefix);
            if (!legacyPrefix.equals(primaryPrefix)) {
                deleteByPrefix(bucket, legacyPrefix);
            }
            return;
        }
        String sanitized = sanitizeFileName(fileName);
        if (sanitized == null) {
            throw new IllegalArgumentException("Invalid file name");
        }
        List<String> keys = new ArrayList<>();
        keys.add(buildObjectPath(studentId, sanitized));
        if (!legacyPrefix.equals(primaryPrefix)) {
            keys.add(legacyPrefix + sanitized);
        }
        storageService.delete(bucket, keys.stream().distinct().toList());
    }

    public StorageDownload download(UUID studentId, String fileName) {
        assertStorageEnabled();
        if (studentId == null || !StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("fileName is required");
        }
        String sanitized = sanitizeFileName(fileName);
        String primaryPrefix = resolveStoragePrefix(studentId);
        String objectPath = primaryPrefix + "/" + sanitized;
        try {
            return storageService.download(storageProperties.getFaceImageBucket(), objectPath);
        } catch (RuntimeException ex) {
            String legacyPath = studentId + "/" + sanitized;
            if (!legacyPath.equals(objectPath)) {
                try {
                    return storageService.download(storageProperties.getFaceImageBucket(), legacyPath);
                } catch (RuntimeException ignored) {
                    // fall through to rethrow original exception
                }
            }
            throw ex;
        }
    }

    private void assertStorageEnabled() {
        if (!storageService.isEnabled()) {
            String reason = storageService.getDisabledReason();
            if (!StringUtils.hasText(reason)) {
                reason = "Supabase storage integration is disabled";
            }
            throw new IllegalStateException(reason);
        }
    }

    private String buildObjectPath(UUID studentId, String fileName) {
        String sanitized = sanitizeFileName(fileName);
        if (sanitized == null) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return resolveStoragePrefix(studentId) + "/" + sanitized;
    }

    private String sanitizeFileName(String original) {
        if (!StringUtils.hasText(original)) {
            return null;
        }
        String name = original.trim();
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0 && lastSeparator + 1 < name.length()) {
            name = name.substring(lastSeparator + 1);
        }
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String resolveObjectKey(String prefix, StorageObjectDto object) {
        String path = object != null ? object.getPath() : null;
        if (StringUtils.hasText(path)) {
            return path;
        }
        String name = object != null ? object.getName() : null;
        if (!StringUtils.hasText(name)) {
            return prefix;
        }
        if (name.startsWith(prefix)) {
            return name;
        }
        return prefix + name;
    }

    private String resolveStoragePrefix(UUID profileId) {
        if (profileId == null) {
            return "unknown";
        }
        UUID userId = profileRepository.findByUserId(profileId)
                .map(ProfileEntity::getUserId)
                .orElseGet(() -> profileRepository.findById(profileId)
                        .map(ProfileEntity::getUserId)
                        .orElse(null));
        if (userId != null) {
            return userId.toString();
        }
        log.debug("Profile {} not found in repository; defaulting storage prefix to profile id", profileId);
        return profileId.toString();
    }

    private void deleteByPrefix(String bucket, String prefix) {
        List<StorageObjectDto> objects = storageService.list(bucket, prefix, 1000, 0, "name", "asc");
        if (objects == null || objects.isEmpty()) {
            return;
        }
        List<String> keys = objects.stream()
                .map(obj -> resolveObjectKey(prefix, obj))
                .filter(StringUtils::hasText)
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        storageService.delete(bucket, keys);
    }

    private MediaType resolveMediaType(MultipartFile file) {
        if (file == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }
        return MediaTypeFactory.getMediaType(file.getOriginalFilename())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private String guessExtension(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/jpeg", "image/jpg" -> ".jpg";
            default -> ".dat";
        };
    }

    private String buildDownloadUrl(UUID studentId, String fileName) {
        return "/api/storage/face-images/" + studentId + "/download?fileName="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    private String buildPublicUrl(String objectPath) {
        if (!StringUtils.hasText(storageProperties.getPublicBaseUrl())) {
            return null;
        }
        String base = storageProperties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            return base + objectPath;
        }
        return base + "/" + objectPath;
    }
}
