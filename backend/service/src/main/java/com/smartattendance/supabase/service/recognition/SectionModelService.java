package com.smartattendance.supabase.service.recognition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import com.smartattendance.config.AttendanceProperties;
import com.smartattendance.supabase.auth.OutboundAuth;
import com.smartattendance.supabase.auth.SupabaseGoTrueProperties;
import com.smartattendance.supabase.config.SupabaseStorageProperties;
import com.smartattendance.supabase.entity.SectionEntity;
import com.smartattendance.supabase.entity.StudentEnrollmentEntity;
import com.smartattendance.supabase.repository.SectionRepository;
import com.smartattendance.supabase.repository.StudentEnrollmentRepository;
import com.smartattendance.vision.ModelManager;
import com.smartattendance.vision.Recognizer;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.service.profile.StudentDirectoryService;
import com.smartattendance.supabase.service.supabase.SupabaseStorageService.StorageObjectHead;
import com.smartattendance.supabase.service.supabase.SupabaseStorageService;

@Service
public class SectionModelService {

    private static final Logger log = LoggerFactory.getLogger(SectionModelService.class);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ZIP_REFRESH_MAX_WAIT = Duration.ofSeconds(6);
    private static final Duration ZIP_REFRESH_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final String CURRENT_MODEL_VERSION = "current";
    private static final String SECTION_FACE_ZIPPER_BASE_URL =
            "https://thczhszfvqdyurrvxjvm.supabase.co/functions/v1";
    private static final String SECTION_FACE_ZIPPER_PATH = "/section-face-zipper";
    private static final String COMPANION_CASCADE_ENDPOINT = "/companion/assets/cascade";
    private static final String COMPANION_MODEL_ENDPOINT_TEMPLATE = "/companion/sections/%s/models/lbph";
    private static final String COMPANION_LABELS_ENDPOINT_TEMPLATE = "/companion/sections/%s/models/labels";

    private final StudentEnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final StudentDirectoryService studentDirectoryService;
    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;
    private final AttendanceProperties attendanceProperties;
    private final ModelManager modelManager;
    private final TransactionTemplate transactionTemplate;
    private final WebClient sectionZipFunctionClient;
    private final String sectionZipAnonKey;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "section-model-trainer");
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentMap<UUID, RecognizerHolder> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public SectionModelService(StudentEnrollmentRepository enrollmentRepository,
                               SectionRepository sectionRepository,
                               StudentDirectoryService studentDirectoryService,
                               SupabaseStorageService storageService,
                               SupabaseStorageProperties storageProperties,
                               AttendanceProperties attendanceProperties,
                               ModelManager modelManager,
                               PlatformTransactionManager transactionManager,
                               WebClient.Builder webClientBuilder,
                               SupabaseGoTrueProperties goTrueProperties) {
        this.enrollmentRepository = enrollmentRepository;
        this.sectionRepository = sectionRepository;
        this.studentDirectoryService = studentDirectoryService;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.attendanceProperties = attendanceProperties;
        this.modelManager = modelManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        String rawAnonKey = goTrueProperties != null ? goTrueProperties.getAnonKey() : null;
        this.sectionZipAnonKey = StringUtils.hasText(rawAnonKey) ? rawAnonKey.trim() : null;
        WebClient functionClient = null;
        try {
            WebClient.Builder builder = webClientBuilder.clone()
                    .baseUrl(SECTION_FACE_ZIPPER_BASE_URL);
            functionClient = builder.build();
        } catch (RuntimeException ex) {
            log.warn("Failed to initialize section-face-zipper client: {}", ex.getMessage());
        }
        this.sectionZipFunctionClient = functionClient;
    }

    /** Resolve a recognizer for the given section, loading it from storage if necessary. */
    public Recognizer resolveRecognizer(UUID sectionId) {
        if (sectionId == null) {
            return null;
        }
        String storagePath = transactionTemplate.execute(status -> sectionRepository.findById(sectionId)
                .map(SectionEntity::getModelStoragePath)
                .orElse(null));
        if (!StringUtils.hasText(storagePath)) {
            cache.remove(sectionId);
            return null;
        }
        final String resolvedPath = storagePath;
        return executeWithStorageBearer(() -> {
            RecognizerHolder holder = cache.get(sectionId);
            if (holder == null || !resolvedPath.equals(holder.storagePath())) {
                holder = loadRecognizer(sectionId, resolvedPath);
                if (holder != null) {
                    cache.put(sectionId, holder);
                }
            }
            return holder != null ? holder.recognizer() : null;
        });
    }

    /** Ensure a section has a persisted model, bootstrapping an empty recognizer if needed. */
    public void ensureSectionModelInitialized(UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        if (!storageService.isEnabled()) {
            log.warn("Supabase storage integration disabled; cannot bootstrap section {}", sectionId);
            return;
        }
        SectionEntity section = transactionTemplate.execute(status -> sectionRepository.findById(sectionId)
                .orElse(null));
        if (section == null) {
            log.debug("Section {} not found; cannot bootstrap model", sectionId);
            return;
        }
        if (Boolean.FALSE.equals(section.getActive())) {
            log.debug("Section {} inactive; skipping bootstrap", sectionId);
            return;
        }
        if (StringUtils.hasText(section.getModelStoragePath())) {
            resolveRecognizer(sectionId);
            return;
        }
        ReentrantLock lock = locks.computeIfAbsent(sectionId, id -> new ReentrantLock());
        lock.lock();
        try {
            String existingPath = transactionTemplate.execute(status -> sectionRepository.findById(sectionId)
                    .map(SectionEntity::getModelStoragePath)
                    .orElse(null));
            if (StringUtils.hasText(existingPath)) {
                resolveRecognizer(sectionId);
                return;
            }
            Path stagingDir = Files.createTempDirectory(attendanceProperties.directories().modelDir(),
                    sectionId + "-bootstrap-");
            Recognizer recognizer = modelManager.createRecognizer();
            recognizer.saveModel(stagingDir);
            ensureBootstrapArtifacts(stagingDir);
            String storagePrefix = resolveStoragePrefix(sectionId);
            executeWithStorageBearer(() -> {
                try {
                    uploadModelArtifacts(storagePrefix, stagingDir);
                    return null;
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to upload bootstrap model for section " + sectionId, ex);
                }
            });
            persistSectionStoragePath(sectionId, storagePrefix);
            Path target = resolveLocalModelRoot(sectionId);
            replaceDirectory(stagingDir, target);
            Recognizer live = modelManager.createRecognizer();
            live.loadModel(target);
            cache.put(sectionId, new RecognizerHolder(live, storagePrefix, target));
            log.info("Bootstrapped LBPH model for section {} (storage={})", sectionId, storagePrefix);
        } catch (IOException ex) {
            log.error("Failed to bootstrap section {} model: {}", sectionId, ex.getMessage(), ex);
        } finally {
            lock.unlock();
        }
    }

    /** Retrain models for all sections the student is actively enrolled in. */
    public CompletableFuture<Void> retrainSectionsForStudent(UUID studentId) {
        if (studentId == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<StudentEnrollmentEntity> enrollments = transactionTemplate.execute(status ->
                enrollmentRepository.findByStudentIdAndActiveTrue(studentId));
        if (enrollments == null || enrollments.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Set<UUID> sectionIds = new HashSet<>();
        for (StudentEnrollmentEntity enrollment : enrollments) {
            if (enrollment.getSectionId() != null) {
                sectionIds.add(enrollment.getSectionId());
            }
        }
        if (sectionIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID sectionId : sectionIds) {
            futures.add(retrainSectionAsync(sectionId));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /** Triggers an asynchronous retrain for the given section. */
    public CompletableFuture<Void> retrainSectionAsync(UUID sectionId) {
        if (sectionId == null) {
            return CompletableFuture.completedFuture(null);
        }
        String bearer = captureStorageBearer();
        return CompletableFuture.runAsync(() -> executeWithStorageBearer(bearer, () -> {
                    retrainSectionInternal(sectionId);
                    return null;
                }), executor)
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof RuntimeException && ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof SectionModelTrainingException trainingException) {
                        log.warn("Unable to retrain section {}: {}", sectionId, trainingException.getMessage());
                    } else {
                        log.error("Failed to retrain section {}: {}", sectionId, ex.getMessage(), ex);
                    }
                    return null;
                });
    }

    public SectionRetrainResult retrainSectionSync(UUID sectionId) {
        if (sectionId == null) {
            return null;
        }
        return executeWithStorageBearer(() -> retrainSectionInternal(sectionId));
    }

    /** Purge stored assets for a section when it is deactivated. */
    public void deactivateSection(UUID sectionId, String knownStoragePath) {
        purgeSectionModel(sectionId, knownStoragePath, true);
    }

    /** Remove cached and stored models when the section row is deleted. */
    public void handleSectionDeleted(UUID sectionId, String knownStoragePath) {
        purgeSectionModel(sectionId, knownStoragePath, false);
    }

    private SectionRetrainResult retrainSectionInternal(UUID sectionId) {
        if (!storageService.isEnabled()) {
            throw new SectionModelTrainingException("Supabase storage integration disabled; cannot retrain section " + sectionId);
        }
        log.info("Retrain pipeline started for section {}", sectionId);
        SectionSnapshot snapshot = loadSnapshot(sectionId);
        if (snapshot == null) {
            throw new SectionModelTrainingException("Section not found: " + sectionId);
        }
        if (snapshot.studentIds().isEmpty()) {
            throw new SectionModelTrainingException("Section " + sectionId + " has no active enrollments");
        }
        log.info("Loaded section {} snapshot with {} active students", sectionId, snapshot.studentIds().size());
        ReentrantLock lock = locks.computeIfAbsent(sectionId, id -> new ReentrantLock());
        lock.lock();
        Path tempRoot = null;
        Path stagingDir = null;
        try {
            tempRoot = Files.createTempDirectory(attendanceProperties.directories().dataDir(),
                    "section-" + sectionId + "-");
            Path datasetRoot = tempRoot.resolve("dataset");
            Files.createDirectories(datasetRoot);
            PreparedDataset dataset = prepareDataset(sectionId, snapshot.studentIds(), datasetRoot);
            Map<UUID, String> labelDisplayNames = resolveLabelDisplayNames(snapshot.studentIds());
            if (dataset.imageCount() < 2) {
                throw new SectionModelTrainingException(
                        "Insufficient images (" + dataset.imageCount() + ") to retrain section " + sectionId,
                        dataset.missingStudentIds());
            }
            Recognizer trained = modelManager.createRecognizer();
            log.info("Training LBPH model for section {} using {} images", sectionId, dataset.imageCount());
            trained.train(dataset.root());
            stagingDir = Files.createTempDirectory(attendanceProperties.directories().modelDir(),
                    sectionId + "-");
            trained.saveModel(stagingDir);
            log.info("Persisted trained artifacts for section {} to staging directory {}", sectionId, stagingDir);
            Path modelFile = stagingDir.resolve("lbph.yml");
            if (!Files.exists(modelFile)) {
                throw new SectionModelTrainingException("Model artifact missing after training section " + sectionId,
                        dataset.missingStudentIds());
            }
            String storagePrefix = resolveStoragePrefix(sectionId);
            log.info("Uploading section {} model artifacts to {}/{}", sectionId, storageProperties.getFaceModelBucket(), storagePrefix);
            uploadModelArtifacts(storagePrefix, stagingDir);
            persistSectionStoragePath(sectionId, storagePrefix);
            Path target = resolveLocalModelRoot(sectionId);
            replaceDirectory(stagingDir, target);
            Recognizer live = modelManager.createRecognizer();
            live.loadModel(target);
            cache.put(sectionId, new RecognizerHolder(live, storagePrefix, target));
            log.info("Section {} retrain completed with {} images (storage={})", sectionId, dataset.imageCount(), storagePrefix);
            return new SectionRetrainResult(
                    sectionId,
                    storagePrefix,
                    dataset.imageCount(),
                    dataset.missingStudentIds(),
                    String.format(COMPANION_MODEL_ENDPOINT_TEMPLATE, sectionId),
                    String.format(COMPANION_LABELS_ENDPOINT_TEMPLATE, sectionId),
                    COMPANION_CASCADE_ENDPOINT,
                    labelDisplayNames);
        } catch (IOException ex) {
            throw new SectionModelTrainingException("Failed to retrain section " + sectionId + ": " + ex.getMessage(), ex);
        } finally {
            deleteRecursively(tempRoot);
            deleteRecursively(stagingDir);
            lock.unlock();
        }
    }

    private SectionSnapshot loadSnapshot(UUID sectionId) {
        return transactionTemplate.execute(status -> sectionRepository.findById(sectionId)
                .map(section -> {
                    List<StudentEnrollmentEntity> enrollments = enrollmentRepository.findBySectionIdAndActiveTrue(sectionId);
                    List<UUID> studentIds = new ArrayList<>();
                    for (StudentEnrollmentEntity enrollment : enrollments) {
                        if (enrollment.getStudentId() != null) {
                            studentIds.add(enrollment.getStudentId());
                        }
                    }
                    return new SectionSnapshot(section.getId(), section.getSectionCode(),
                            section.getModelStoragePath(), Collections.unmodifiableList(studentIds));
                })
                .orElse(null));
    }

    private Map<UUID, String> resolveLabelDisplayNames(List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StudentDto> profiles = studentDirectoryService.findByIds(studentIds);
        LinkedHashMap<UUID, String> labels = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            StudentDto dto = profiles.get(studentId);
            String label = null;
            if (dto != null) {
                String fullName = dto.getFullName();
                if (fullName != null && !fullName.isBlank()) {
                    label = fullName.trim();
                } else if (dto.getStudentNumber() != null && !dto.getStudentNumber().isBlank()) {
                    label = dto.getStudentNumber().trim();
                }
            }
            if (label == null || label.isBlank()) {
                label = studentId != null ? studentId.toString() : "";
            }
            labels.put(studentId, label);
        }
        return Map.copyOf(labels);
    }

    private PreparedDataset prepareDataset(UUID sectionId,
                                           List<UUID> studentIds,
                                           Path datasetRoot) throws IOException {
        String bucket = storageProperties.getFaceZipBucket();
        String objectPath = sectionId + "/faces.zip";
        awaitFacesArchiveRefresh(sectionId);
        byte[] zipBytes = downloadBytes(bucket, objectPath);
        if (zipBytes == null || zipBytes.length == 0) {
            throw new SectionModelTrainingException(
                    "Faces archive missing for section " + sectionId + " at " + objectPath);
        }
        log.info("Downloaded faces archive for section {} from bucket {} ({} bytes)",
                sectionId, bucket, zipBytes.length);
        extractZipToDirectory(zipBytes, datasetRoot);
        Path trainingRoot = resolveDatasetRoot(datasetRoot, studentIds);
        Map<String, Long> counts = countImagesByLabel(trainingRoot);
        long totalImages = counts.values().stream().mapToLong(Long::longValue).sum();
        List<UUID> missingStudents = new ArrayList<>();
        if (studentIds != null && !studentIds.isEmpty()) {
            for (UUID studentId : studentIds) {
                String label = studentId.toString();
                long contributed = counts.getOrDefault(label, 0L);
                if (contributed == 0L) {
                    log.warn("Section {} student {} has no images in faces.zip", sectionId, studentId);
                    missingStudents.add(studentId);
                } else {
                    log.info("Section {} student {} contributed {} images from faces.zip", sectionId, studentId, contributed);
                }
            }
        } else {
            counts.forEach((label, contributed) ->
                    log.info("Section {} label {} contributed {} images from faces.zip", sectionId, label, contributed));
        }
        log.info("Dataset assembly complete for section {} with {} total images from faces.zip", sectionId, totalImages);
        return new PreparedDataset(trainingRoot, totalImages, missingStudents);
    }

    private void awaitFacesArchiveRefresh(UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        ArchiveMetadata before = fetchFacesArchiveMetadata(sectionId);
        triggerSectionFaceZipper(sectionId);
        if (before.accessible()) {
            OffsetDateTime updated = waitForFacesArchiveUpdate(sectionId, before);
            if (updated != null) {
                log.info("faces.zip for section {} refreshed at {}", sectionId, updated);
            } else {
                log.warn("faces.zip metadata for section {} did not change within {} seconds; proceeding",
                        sectionId, ZIP_REFRESH_MAX_WAIT.getSeconds());
            }
        } else {
            log.info("faces.zip metadata unavailable for section {}; waiting {} seconds before download",
                    sectionId, ZIP_REFRESH_MAX_WAIT.getSeconds());
            sleep(ZIP_REFRESH_MAX_WAIT);
        }
    }

    private void triggerSectionFaceZipper(UUID sectionId) {
        if (sectionZipFunctionClient == null) {
            log.warn("section-face-zipper client unavailable; skipping refresh trigger for section {}", sectionId);
            return;
        }
        try {
            String bearer = captureStorageBearer();
            String anonKey = requireSectionZipAnonKey();
            sectionZipFunctionClient.post()
                    .uri(builder -> builder.path(SECTION_FACE_ZIPPER_PATH)
                            .queryParam("sectionId", sectionId)
                            .build())
                    .headers(headers -> {
                        headers.set(HttpHeaders.AUTHORIZATION, bearer);
                        headers.set("apikey", anonKey);
                    })
                    .retrieve()
                    .toBodilessEntity()
                    .block(ZIP_REFRESH_MAX_WAIT);
            log.info("Triggered section-face-zipper for section {}", sectionId);
        } catch (RuntimeException ex) {
            log.warn("Failed to trigger section-face-zipper for section {}: {}", sectionId, ex.getMessage());
        }
    }

    private OffsetDateTime waitForFacesArchiveUpdate(UUID sectionId, ArchiveMetadata previous) {
        long deadline = System.nanoTime() + ZIP_REFRESH_MAX_WAIT.toNanos();
        boolean previousExists = previous != null && previous.exists();
        OffsetDateTime previousUpdatedAt = previous != null ? previous.updatedAt() : null;
        while (System.nanoTime() < deadline) {
            sleep(ZIP_REFRESH_POLL_INTERVAL);
            ArchiveMetadata current = fetchFacesArchiveMetadata(sectionId);
            if (!current.accessible()) {
                return null;
            }
            if (!current.exists()) {
                continue;
            }
            OffsetDateTime updatedAt = current.updatedAt();
            if (updatedAt != null) {
                if (!previousExists || previousUpdatedAt == null || updatedAt.isAfter(previousUpdatedAt)) {
                    return updatedAt;
                }
            }
        }
        return null;
    }

    private ArchiveMetadata fetchFacesArchiveMetadata(UUID sectionId) {
        try {
            String bucket = storageProperties.getFaceZipBucket();
            String objectPath = sectionId + "/faces.zip";
            StorageObjectHead head = storageService.head(bucket, objectPath);
            if (head == null) {
                return new ArchiveMetadata(null, false, false);
            }
            return new ArchiveMetadata(head.lastModified(), head.accessible(), head.exists());
        } catch (RuntimeException ex) {
            log.debug("Unable to fetch faces.zip metadata for section {}: {}", sectionId, ex.getMessage());
            return new ArchiveMetadata(null, false, false);
        }
    }

    private void sleep(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void extractZipToDirectory(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!StringUtils.hasText(entryName)) {
                    zip.closeEntry();
                    continue;
                }
                Path resolved = targetDir.resolve(entryName).normalize();
                if (!resolved.startsWith(targetDir)) {
                    zip.closeEntry();
                    throw new IOException("faces.zip entry escapes target directory: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Path parent = resolved.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private Path resolveDatasetRoot(Path datasetRoot, List<UUID> studentIds) throws IOException {
        Path current = datasetRoot;
        Set<String> expectedLabels = studentIds == null ? Set.of()
                : studentIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet());
        boolean adjusted;
        do {
            adjusted = false;
            try (var stream = Files.list(current)) {
                List<Path> children = stream
                        .filter(path -> !path.getFileName().toString().startsWith("__MACOSX"))
                        .collect(java.util.stream.Collectors.toList());
                if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                    String name = children.get(0).getFileName().toString();
                    if (!expectedLabels.contains(name)) {
                        current = children.get(0);
                        adjusted = true;
                    }
                }
            }
        } while (adjusted);
        return current;
    }

    private Map<String, Long> countImagesByLabel(Path trainingRoot) throws IOException {
        Map<String, Long> counts = new HashMap<>();
        if (!Files.exists(trainingRoot)) {
            return counts;
        }
        try (var files = Files.walk(trainingRoot)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                Path relative = trainingRoot.relativize(path);
                if (relative.getNameCount() == 0) {
                    return;
                }
                String label = relative.getName(0).toString();
                if (!StringUtils.hasText(label) || label.startsWith(".")) {
                    return;
                }
                counts.merge(label, 1L, Long::sum);
            });
        }
        return counts;
    }

    private byte[] downloadBytes(String bucket, String objectPath) {
        try {
            log.info("Downloading '{}' from Supabase bucket '{}'", objectPath, bucket);
            byte[] data = storageService.downloadAsBytes(bucket, objectPath, DOWNLOAD_TIMEOUT);
            if (data == null || data.length == 0) {
                log.warn("Empty body for '{}' in bucket '{}'", objectPath, bucket);
                return null;
            }
            log.info("Successfully read {} bytes from '{}' in bucket '{}'", data.length, objectPath, bucket);
            return data;
        } catch (WebClientResponseException ex) {
            log.warn("Failed to download '{}' from bucket '{}' (status={}): {}", objectPath, bucket,
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return null;
        } catch (RuntimeException ex) {
            log.warn("Failed to download '{}' from bucket '{}': {}", objectPath, bucket, ex.getMessage());
            return null;
        }
    }

    private String captureStorageBearer() {
        return OutboundAuth.resolveBearerToken()
                .orElseGet(() -> {
                    String fallback = storageProperties != null ? storageProperties.getServiceAccessToken() : null;
                    if (!StringUtils.hasText(fallback)) {
                        throw new IllegalStateException(
                                "No Supabase JWT available for storage operations; ensure the request is authenticated or configure supabase.storage.service-access-token with a service user token");
                    }
                    return OutboundAuth.ensureBearerFormat(fallback);
                });
    }

    private String requireSectionZipAnonKey() {
        if (!StringUtils.hasText(sectionZipAnonKey)) {
            throw new IllegalStateException("Supabase anon key is not configured; cannot invoke section-face-zipper");
        }
        return sectionZipAnonKey;
    }

    private <T> T executeWithStorageBearer(String bearer, Supplier<T> action) {
        return storageService.withBearer(bearer, action);
    }

    private <T> T executeWithStorageBearer(Supplier<T> action) {
        return executeWithStorageBearer(captureStorageBearer(), action);
    }

    private void executeWithStorageBearer(String bearer, Runnable action) {
        executeWithStorageBearer(bearer, () -> {
            action.run();
            return null;
        });
    }

    private void executeWithStorageBearer(Runnable action) {
        executeWithStorageBearer(() -> {
            action.run();
            return null;
        });
    }

    private record ArchiveMetadata(OffsetDateTime updatedAt, boolean accessible, boolean exists) {
    }

    private byte[] createModelArchive(Path modelDir) throws IOException {
        if (modelDir == null || !Files.exists(modelDir)) {
            return new byte[0];
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walkFileTree(modelDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(modelDir)) {
                        String entryName = modelDir.relativize(dir).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!Files.isRegularFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String entryName = modelDir.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return baos.toByteArray();
    }

    private void uploadModelArtifacts(String storagePrefix, Path stagingDir) throws IOException {
        String bucket = storageProperties.getFaceModelBucket();
        String folder = storagePrefix.endsWith("/") ? storagePrefix : storagePrefix + "/";
        log.info("Uploading artifacts to bucket {} under prefix {}", bucket, folder);
        try {
            storageService.delete(bucket, List.of(folder));
        } catch (RuntimeException ex) {
            log.warn("Failed to clear existing model artifacts for prefix {} in bucket {}: {}", folder, bucket,
                    ex.getMessage());
        }
        byte[] zipBytes = createModelArchive(stagingDir);
        storageService.upload(bucket,
                folder + "lbph.zip",
                MediaType.APPLICATION_OCTET_STREAM,
                zipBytes,
                true);
        log.info("Uploaded LBPH model archive for prefix {}", folder);
    }

    private void persistSectionStoragePath(UUID sectionId, String storagePrefix) {
        transactionTemplate.executeWithoutResult(status -> {
            SectionEntity entity = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
            entity.setModelStoragePath(storagePrefix);
            entity.setUpdatedAt(OffsetDateTime.now());
            sectionRepository.save(entity);
        });
    }

    private String resolveStoragePrefix(UUID sectionId) {
        return sectionId + "/" + CURRENT_MODEL_VERSION;
    }

    private RecognizerHolder loadRecognizer(UUID sectionId, String storagePath) {
        ReentrantLock lock = locks.computeIfAbsent(sectionId, id -> new ReentrantLock());
        lock.lock();
        try {
            Path target = resolveLocalModelRoot(sectionId);
            Path staging = Files.createTempDirectory(attendanceProperties.directories().modelDir(),
                    sectionId + "-load-");
            log.info("Loading recognizer for section {} from storage path {}", sectionId, storagePath);
            byte[] zipBytes = downloadBytes(storageProperties.getFaceModelBucket(), storagePath + "/lbph.zip");
            if (zipBytes == null || zipBytes.length == 0) {
                deleteRecursively(staging);
                log.warn("Model archive for section {} not found at {}", sectionId, storagePath);
                return null;
            }
            extractZipToDirectory(zipBytes, staging);
            replaceDirectory(staging, target);
            Recognizer recognizer = modelManager.createRecognizer();
            recognizer.loadModel(target);
            return new RecognizerHolder(recognizer, storagePath, target);
        } catch (IOException ex) {
            log.error("Failed to load recognizer for section {}: {}", sectionId, ex.getMessage(), ex);
            return null;
        } finally {
            lock.unlock();
        }
    }

    private Path resolveLocalModelRoot(UUID sectionId) throws IOException {
        Path modelRoot = attendanceProperties.directories().modelDir().resolve("sections");
        Files.createDirectories(modelRoot);
        return modelRoot.resolve(sectionId.toString());
    }

    private void replaceDirectory(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            deleteRecursively(target);
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.debug("Failed to delete {}: {}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.debug("Failed to traverse {} for deletion: {}", root, ex.getMessage());
        }
    }

    public byte[] fetchModelArtifact(UUID sectionId, String artifactName) {
        if (sectionId == null || !StringUtils.hasText(artifactName)) {
            return null;
        }
        String normalized = artifactName.trim();
        if (!normalized.equals("lbph.yml") && !normalized.equals("labels.txt")) {
            log.warn("Unsupported model artifact request '{}' for section {}", artifactName, sectionId);
            return null;
        }
        log.info("Fetching model artifact '{}' for section {}", normalized, sectionId);
        String storagePath = transactionTemplate.execute(status -> sectionRepository.findById(sectionId)
                .map(SectionEntity::getModelStoragePath)
                .orElse(null));
        if (!StringUtils.hasText(storagePath)) {
            log.warn("Section {} has no stored model path; cannot fetch {}", sectionId, artifactName);
            return null;
        }
        String bucket = storageProperties.getFaceModelBucket();
        String objectPath = storagePath + "/lbph.zip";
        log.info("Downloading model archive {} from bucket {} for section {}", objectPath, bucket, sectionId);
        return executeWithStorageBearer(() -> {
            byte[] zipBytes = downloadBytes(bucket, objectPath);
            if (zipBytes == null || zipBytes.length == 0) {
                log.warn("Model archive {} was empty for section {}", objectPath, sectionId);
                return null;
            }
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals(normalized)) {
                        byte[] data = zis.readAllBytes();
                        log.info("Extracted {} ({} bytes) for section {}", normalized, data.length, sectionId);
                        return data;
                    }
                    zis.closeEntry();
                }
                log.warn("Artifact {} not found inside archive {} for section {}", normalized, objectPath, sectionId);
            } catch (IOException ex) {
                log.warn("Failed to extract {} from archive for section {}: {}", artifactName, sectionId, ex.getMessage());
            }
            return null;
        });
    }

    public byte[] fetchCompressedModelArtifact(UUID sectionId, String artifactName) {
        byte[] artifact = fetchModelArtifact(sectionId, artifactName);
        if (artifact == null || artifact.length == 0) {
            return artifact;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(artifactName);
            zos.putNextEntry(entry);
            zos.write(artifact);
            zos.closeEntry();
            zos.finish();
            byte[] compressed = baos.toByteArray();
            log.info("Compressed artifact '{}' for section {} from {} bytes to {} bytes",
                    artifactName,
                    sectionId,
                    artifact.length,
                    compressed.length);
            return compressed;
        } catch (IOException ex) {
            log.warn("Failed to compress artifact '{}' for section {}: {}",
                    artifactName,
                    sectionId,
                    ex.getMessage());
            return null;
        }
    }
    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private record RecognizerHolder(Recognizer recognizer, String storagePath, Path localPath) {
    }

    private record SectionSnapshot(UUID id, String code, String storagePath, List<UUID> studentIds) {
    }

    private void purgeSectionModel(UUID sectionId, String knownStoragePath, boolean updateDatabase) {
        if (sectionId == null) {
            return;
        }
        ReentrantLock lock = locks.computeIfAbsent(sectionId, id -> new ReentrantLock());
        lock.lock();
        try {
            String storagePath = StringUtils.hasText(knownStoragePath) ? knownStoragePath
                    : transactionTemplate.execute(status -> sectionRepository.findById(sectionId)
                            .map(SectionEntity::getModelStoragePath)
                            .orElse(null));
            if (StringUtils.hasText(storagePath) && storageService.isEnabled()) {
                executeWithStorageBearer(() -> {
                    storageService.delete(storageProperties.getFaceModelBucket(), List.of(storagePath));
                    return null;
                });
            }
            cache.remove(sectionId);
            try {
                Path local = resolveLocalModelRoot(sectionId);
                deleteRecursively(local);
            } catch (IOException ex) {
                log.debug("Failed to remove local model for section {}: {}", sectionId, ex.getMessage());
            }
            if (updateDatabase) {
                transactionTemplate.executeWithoutResult(status -> sectionRepository.findById(sectionId)
                        .ifPresent(entity -> {
                            entity.setModelStoragePath(null);
                            entity.setUpdatedAt(OffsetDateTime.now());
                            sectionRepository.save(entity);
                        }));
            }
            log.info("Purged model artifacts for section {}", sectionId);
        } finally {
            lock.unlock();
            if (!updateDatabase) {
                locks.remove(sectionId, lock);
            }
        }
    }

    private void ensureBootstrapArtifacts(Path stagingDir) throws IOException {
        Path modelFile = stagingDir.resolve("lbph.yml");
        if (!Files.exists(modelFile)) {
            Files.createFile(modelFile);
        }
        Path labelsFile = stagingDir.resolve("labels.txt");
        if (!Files.exists(labelsFile)) {
            Files.createFile(labelsFile);
        }
    }

    public static class SectionModelTrainingException extends RuntimeException {
        private final List<UUID> missingStudentIds;

        public SectionModelTrainingException(String message) {
            super(message);
            this.missingStudentIds = List.of();
        }

        public SectionModelTrainingException(String message, Throwable cause) {
            super(message, cause);
            this.missingStudentIds = List.of();
        }

        public SectionModelTrainingException(String message, List<UUID> missingStudentIds) {
            super(message);
            this.missingStudentIds = missingStudentIds != null ? List.copyOf(missingStudentIds) : List.of();
        }

        public List<UUID> getMissingStudentIds() {
            return missingStudentIds;
        }
    }

    public record SectionRetrainResult(
            UUID sectionId,
            String storagePrefix,
            long imageCount,
            List<UUID> missingStudentIds,
            String modelDownloadPath,
            String labelsDownloadPath,
            String cascadeDownloadPath,
            Map<UUID, String> labelDisplayNames) {
        public SectionRetrainResult {
            missingStudentIds = missingStudentIds != null ? List.copyOf(missingStudentIds) : List.of();
            labelDisplayNames = labelDisplayNames != null ? Map.copyOf(labelDisplayNames) : Map.of();
        }
    }

    private record PreparedDataset(Path root, long imageCount, List<UUID> missingStudentIds) {
        private PreparedDataset {
            missingStudentIds = missingStudentIds != null ? List.copyOf(missingStudentIds) : List.of();
        }
    }
}
