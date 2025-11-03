package com.smartattendance.supabase.service.face;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartattendance.supabase.config.SupabaseStorageProperties;
import com.smartattendance.supabase.dto.FaceDataCreateRequest;
import com.smartattendance.supabase.dto.FaceDataDeleteRequest;
import com.smartattendance.supabase.dto.FaceDataDto;
import com.smartattendance.supabase.dto.FaceDataStatusResponse;
import com.smartattendance.supabase.dto.StorageObjectDto;
import com.smartattendance.supabase.entity.FaceDataEntity;
import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.repository.FaceDataRepository;
import com.smartattendance.supabase.repository.ProfileRepository;

import com.smartattendance.supabase.service.supabase.SupabaseStorageService;

@Service
public class FaceDataAdminService {

    private static final Logger log = LoggerFactory.getLogger(FaceDataAdminService.class);
    private static final Set<String> KNOWN_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "heic", "heif", "avif", "ppm", "pgm", "pbm");

    private final FaceDataRepository faceDataRepository;
    private final ProfileRepository profileRepository;
    private final ObjectMapper mapper;
    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;

    public FaceDataAdminService(FaceDataRepository faceDataRepository,
                                ProfileRepository profileRepository,
                                ObjectMapper mapper,
                                SupabaseStorageService storageService,
                                SupabaseStorageProperties storageProperties) {
        this.faceDataRepository = faceDataRepository;
        this.profileRepository = profileRepository;
        this.mapper = mapper;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
    }

    @Transactional(readOnly = true)
    public List<FaceDataDto> listFaceData(UUID userId) {
        ProfileReference profile = findProfile(userId);
        List<FaceDataEntity> entities = profile != null
                ? faceDataRepository.findByStudentId(profile.id())
                : faceDataRepository.findAll();
        Map<UUID, UUID> profileToUser = loadUserMappings(entities);
        if (profile != null) {
            profileToUser.putIfAbsent(profile.id(), profile.userId());
        }
        return entities.stream()
                .map(entity -> toDto(entity, profileToUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public FaceDataDto createFaceData(UUID userId, FaceDataCreateRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("studentId is required");
        }
        if (request == null || request.getImagePath() == null || request.getImagePath().isBlank()) {
            throw new IllegalArgumentException("image_url is required");
        }
        ProfileReference profile = findProfile(userId);
        if (profile == null) {
            throw new IllegalArgumentException("Student profile not found");
        }
        String folderPath = normalizeFolderPath(request.getImagePath());
        if (!StringUtils.hasText(folderPath)) {
            throw new IllegalArgumentException("image_url must reference a storage folder");
        }
        List<FaceDataEntity> existing = faceDataRepository.findByStudentId(profile.id());
        FaceDataEntity selected = existing.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(FaceDataEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        FaceDataEntity entity;
        if (selected == null) {
            entity = new FaceDataEntity();
            entity.setId(UUID.randomUUID());
            entity.setStudentId(profile.id());
        } else {
            entity = selected;
            UUID keepId = entity.getId();
            List<FaceDataEntity> duplicates = existing.stream()
                    .filter(candidate -> candidate != null && candidate.getId() != null
                            && !candidate.getId().equals(keepId))
                    .toList();
            if (!duplicates.isEmpty()) {
                deleteCaptureAssets(duplicates);
                faceDataRepository.deleteAll(duplicates);
            }
            String currentFolder = normalizeFolderPath(entity.getImagePath());
            if (StringUtils.hasText(currentFolder) && !currentFolder.equals(folderPath)) {
                deleteCaptureAssets(entity);
            }
        }
        entity.setStudentId(profile.id());
        entity.setImagePath(folderPath);
        ProcessingStatusNormalizer.NormalizationResult normalized = ProcessingStatusNormalizer
                .normalize(request.getProcessingStatus());
        entity.setProcessingStatus(normalized.value());
        entity.setEmbeddingVector(request.getEmbeddingVector());
        entity.setFaceEncoding(request.getFaceEncoding());
        entity.setMetadata(mergeMetadataWithOriginalStatus(request.getMetadata(), normalized));
        entity.setCreatedAt(OffsetDateTime.now());
        FaceDataEntity saved = faceDataRepository.save(entity);
        return toDto(saved, Map.of(profile.id(), profile.userId()));
    }

    @Transactional
    public void deleteByRequest(FaceDataDeleteRequest request, UUID userId) {
        if (request != null && request.getIds() != null && !request.getIds().isEmpty()) {
            List<FaceDataEntity> entities = faceDataRepository.findAllById(request.getIds());
            deleteCaptureAssets(entities);
            faceDataRepository.deleteAll(entities);
            return;
        }
        ProfileReference profile = findProfile(userId);
        if (profile != null) {
            List<FaceDataEntity> entities = faceDataRepository.findByStudentId(profile.id());
            deleteCaptureAssets(entities);
            faceDataRepository.deleteAll(entities);
        }
    }

    @Transactional(readOnly = true)
    public FaceDataStatusResponse getStatus(UUID userId) {
        FaceDataStatusResponse response = new FaceDataStatusResponse();
        if (userId == null) {
            return response;
        }
        ProfileReference profile = findProfile(userId);
        if (profile == null) {
            return response;
        }
        List<FaceDataEntity> records = faceDataRepository.findByStudentId(profile.id());
        int imageCount = records.stream()
                .mapToInt(this::countImagesForEntity)
                .sum();
        response.setImageCount(imageCount);
        response.setHasFaceData(imageCount > 0);
        if (!records.isEmpty()) {
            records.stream()
                    .max(Comparator.comparing(FaceDataEntity::getCreatedAt))
                    .ifPresent(entity -> {
                        response.setLatestStatus(resolveLatestStatus(entity));
                        response.setUpdatedAt(entity.getCreatedAt());
                    });
        }
        return response;
    }

    private void deleteCaptureAssets(List<FaceDataEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (FaceDataEntity entity : entities) {
            deleteCaptureAssets(entity);
        }
    }

    private void deleteCaptureAssets(FaceDataEntity entity) {
        if (entity == null) {
            return;
        }
        String bucket = storageProperties != null ? storageProperties.getFaceImageBucket() : null;
        if (!StringUtils.hasText(bucket)) {
            return;
        }
        String normalized = normalizeFolderPath(entity.getImagePath());
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        List<String> keys = listStorageKeys(bucket, normalized);
        if (keys.isEmpty()) {
            return;
        }
        if (storageService == null || !storageService.isEnabled()) {
            log.debug("Supabase storage service disabled; skipping deletion for {}", normalized);
            return;
        }
        if (!keys.isEmpty()) {
            storageService.delete(bucket, keys);
        }
    }

    private int countImagesForEntity(FaceDataEntity entity) {
        if (entity == null) {
            return 0;
        }
        String imagePath = entity.getImagePath();
        if (!StringUtils.hasText(imagePath)) {
            return 0;
        }
        String bucket = storageProperties != null ? storageProperties.getFaceImageBucket() : null;
        if (!StringUtils.hasText(bucket)) {
            return 0;
        }
        String normalized = normalizeFolderPath(imagePath);
        if (!StringUtils.hasText(normalized)) {
            return 0;
        }
        List<String> keys = listStorageKeys(bucket, normalized);
        if (!keys.isEmpty()) {
            return keys.size();
        }
        if (storageService == null || !storageService.isEnabled()) {
            return 1;
        }
        return 0;
    }

    private List<String> listStorageKeys(String bucket, String normalized) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(normalized)) {
            return List.of();
        }
        if (storageService == null || !storageService.isEnabled()) {
            return List.of();
        }
        String prefix = normalized.endsWith("/") ? normalized : normalized + "/";
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        int offset = 0;
        int pageSize = 1000;
        try {
            while (true) {
                List<StorageObjectDto> objects = storageService.list(bucket, prefix, pageSize, offset, "name", "asc");
                if (objects == null || objects.isEmpty()) {
                    break;
                }
                for (StorageObjectDto object : objects) {
                    if (object == null) {
                        continue;
                    }
                    if (object.getSize() != null && object.getSize() <= 0) {
                        continue;
                    }
                    String key = resolveObjectKey(prefix, object);
                    key = normalizeStorageKey(key);
                    if (StringUtils.hasText(key) && !key.endsWith("/")) {
                        keys.add(key);
                    }
                }
                if (objects.size() < pageSize) {
                    break;
                }
                offset += objects.size();
            }
        } catch (Exception ex) {
            log.warn("Failed to list storage assets for face capture {}: {}", normalized, ex.getMessage());
            return List.of();
        }
        return keys.isEmpty() ? List.of() : new ArrayList<>(keys);
    }

    private String normalizeFolderPath(String rawPath) {
        String sanitized = sanitizeRawFolderPath(rawPath);
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        String candidate = sanitized;
        String bucket = storageProperties != null ? storageProperties.getFaceImageBucket() : null;
        if (storageService != null && storageService.isEnabled() && StringUtils.hasText(bucket)) {
            String resolved = ensureFolderHasFiles(candidate, bucket);
            if (StringUtils.hasText(resolved)) {
                candidate = resolved;
            } else {
                String fallback = removeFileSegment(candidate);
                candidate = StringUtils.hasText(fallback) ? fallback : candidate;
            }
        } else {
            String fallback = removeFileSegment(candidate);
            candidate = StringUtils.hasText(fallback) ? fallback : candidate;
        }
        candidate = trimSlashes(candidate);
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private String resolveObjectKey(String prefix, StorageObjectDto object) {
        if (object == null) {
            return null;
        }
        String path = object.getPath();
        if (StringUtils.hasText(path)) {
            return path;
        }
        String name = object.getName();
        if (!StringUtils.hasText(name)) {
            return prefix;
        }
        if (name.startsWith(prefix)) {
            return name;
        }
        return prefix + name;
    }

    private String sanitizeRawFolderPath(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String normalized = rawPath.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        normalized = normalized.replace("\\", "/");
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        if (normalized.contains("://")) {
            try {
                normalized = URI.create(normalized).getPath();
            } catch (IllegalArgumentException ex) {
                int schemeIndex = normalized.indexOf("://");
                String remainder = normalized.substring(schemeIndex + 3);
                int slashIndex = remainder.indexOf('/');
                normalized = slashIndex >= 0 ? remainder.substring(slashIndex) : remainder;
            }
        }
        normalized = collapseSlashes(normalized);
        normalized = trimLeadingSlashes(normalized);
        if (storageProperties != null && StringUtils.hasText(storageProperties.getFaceImageBucket())) {
            String bucket = storageProperties.getFaceImageBucket();
            String bucketPrefix = bucket + "/";
            int bucketIndex = normalized.indexOf(bucketPrefix);
            if (bucketIndex >= 0) {
                normalized = normalized.substring(bucketIndex + bucketPrefix.length());
            } else if (normalized.equals(bucket)) {
                normalized = "";
            }
        }
        normalized = trimLeadingSlashes(normalized);
        normalized = trimTrailingSlashes(normalized);
        return normalized;
    }

    private String ensureFolderHasFiles(String candidate, String bucket) {
        String current = trimSlashes(candidate);
        while (StringUtils.hasText(current)) {
            if (folderContainsObjects(bucket, current)) {
                return trimSlashes(current);
            }
            String parent = parentFolder(current);
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = trimSlashes(parent);
        }
        return null;
    }

    private boolean folderContainsObjects(String bucket, String prefix) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(prefix)) {
            return false;
        }
        if (storageService == null || !storageService.isEnabled()) {
            return false;
        }
        String normalizedPrefix = trimSlashes(prefix);
        String queryPrefix = normalizedPrefix.endsWith("/") ? normalizedPrefix : normalizedPrefix + "/";
        try {
            List<StorageObjectDto> objects = storageService.list(bucket, queryPrefix, 1, 0, "name", "asc");
            if (objects == null || objects.isEmpty()) {
                return false;
            }
            for (StorageObjectDto object : objects) {
                if (object == null) {
                    continue;
                }
                if (object.getSize() != null && object.getSize() <= 0) {
                    continue;
                }
                String key = resolveObjectKey(queryPrefix, object);
                key = normalizeStorageKey(key);
                if (StringUtils.hasText(key) && !key.endsWith("/")) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.debug("Failed to verify storage folder {} in bucket {}: {}", prefix, bucket, ex.getMessage());
            return false;
        }
    }

    private String parentFolder(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String trimmed = trimTrailingSlashes(path);
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        if (lastSlash == 0) {
            return "";
        }
        return trimmed.substring(0, lastSlash);
    }

    private String removeFileSegment(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String current = trimTrailingSlashes(path);
        while (StringUtils.hasText(current)) {
            int lastSlash = current.lastIndexOf('/');
            String segment = lastSlash >= 0 ? current.substring(lastSlash + 1) : current;
            if (!looksLikeFileSegment(segment)) {
                break;
            }
            current = lastSlash >= 0 ? current.substring(0, lastSlash) : "";
        }
        current = trimSlashes(current);
        return StringUtils.hasText(current) ? current : null;
    }

    private boolean looksLikeFileSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return false;
        }
        int dotIndex = segment.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == segment.length() - 1) {
            return false;
        }
        String extension = segment.substring(dotIndex + 1).toLowerCase();
        return KNOWN_IMAGE_EXTENSIONS.contains(extension);
    }

    private String normalizeStorageKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String normalized = key.trim().replace("\\", "/");
        normalized = collapseSlashes(normalized);
        normalized = trimLeadingSlashes(normalized);
        if (storageProperties != null && StringUtils.hasText(storageProperties.getFaceImageBucket())) {
            String bucket = storageProperties.getFaceImageBucket();
            String bucketPrefix = bucket + "/";
            if (normalized.startsWith(bucketPrefix)) {
                normalized = normalized.substring(bucketPrefix.length());
            }
        }
        return trimLeadingSlashes(normalized);
    }

    private String collapseSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String collapsed = value;
        while (collapsed.contains("//")) {
            collapsed = collapsed.replace("//", "/");
        }
        return collapsed;
    }

    private String trimSlashes(String value) {
        if (value == null) {
            return null;
        }
        return trimTrailingSlashes(trimLeadingSlashes(value));
    }

    private String trimLeadingSlashes(String value) {
        if (value == null) {
            return null;
        }
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index++;
        }
        return value.substring(index);
    }

    private String trimTrailingSlashes(String value) {
        if (value == null) {
            return null;
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private FaceDataDto toDto(FaceDataEntity entity, Map<UUID, UUID> profileToUser) {
        FaceDataDto dto = new FaceDataDto();
        dto.setId(entity.getId());
        UUID profileId = entity.getStudentId();
        UUID userId = profileToUser != null ? profileToUser.get(profileId) : null;
        dto.setStudentId(userId != null ? userId : profileId);
        dto.setImagePath(entity.getImagePath());
        dto.setProcessingStatus(entity.getProcessingStatus());
        dto.setEmbeddingVector(entity.getEmbeddingVector());
        dto.setFaceEncoding(entity.getFaceEncoding());
        dto.setMetadata(entity.getMetadata());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private Map<UUID, UUID> loadUserMappings(List<FaceDataEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new HashMap<>();
        }
        List<UUID> profileIds = entities.stream()
                .map(FaceDataEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (profileIds.isEmpty()) {
            return new HashMap<>();
        }
        return profileRepository.findByIdIn(profileIds).stream()
                .collect(Collectors.toMap(ProfileEntity::getId,
                        ProfileEntity::getUserId,
                        (existing, replacement) -> existing,
                        HashMap::new));
    }

    private ProfileReference findProfile(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        Optional<ProfileEntity> directMatch = profileRepository.findByUserId(studentId);
        if (directMatch.isPresent()) {
            ProfileEntity profile = directMatch.get();
            return new ProfileReference(profile.getId(), profile.getUserId());
        }
        return profileRepository.findById(studentId)
                .map(profile -> new ProfileReference(profile.getId(), profile.getUserId()))
                .orElse(null);
    }

    private JsonNode mergeMetadataWithOriginalStatus(JsonNode metadata,
            ProcessingStatusNormalizer.NormalizationResult normalized) {
        if (!normalized.changed() || normalized.original() == null || normalized.original().isBlank()) {
            return metadata;
        }
        ObjectNode objectNode;
        if (metadata != null && metadata.isObject()) {
            objectNode = ((ObjectNode) metadata).deepCopy();
        } else {
            objectNode = mapper.createObjectNode();
            if (metadata != null) {
                objectNode.set("payload", metadata);
            }
        }
        objectNode.put("original_status", normalized.original());
        return objectNode;
    }

    private String resolveLatestStatus(FaceDataEntity entity) {
        String status = entity.getProcessingStatus();
        String original = findOriginalStatus(entity.getMetadata());
        if (original != null && !original.isBlank()) {
            return original;
        }
        return status;
    }

    private String findOriginalStatus(JsonNode metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata.has("original_status")) {
            JsonNode node = metadata.get("original_status");
            if (node.isTextual()) {
                return node.asText();
            }
        }
        if (metadata.has("details")) {
            JsonNode details = metadata.get("details");
            if (details != null) {
                if (details.has("original_status") && details.get("original_status").isTextual()) {
                    return details.get("original_status").asText();
                }
            }
        }
        return null;
    }

    private record ProfileReference(UUID id, UUID userId) {
    }
}
