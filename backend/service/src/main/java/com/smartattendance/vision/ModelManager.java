package com.smartattendance.vision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.smartattendance.config.AttendanceProperties;
import com.smartattendance.vision.preprocess.FaceImageProcessor;
import com.smartattendance.vision.recognizer.LBPHRecognizer;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import jakarta.annotation.PreDestroy;

/**
 * Central manager for the live face recognition model.
 * - Loads existing model if present; else trains from faces root.
 * - Applies incremental updates when new students are enrolled.
 * - Swaps models atomically so live recognition uses the latest without blocking.
 */
@Component
public class ModelManager {
    private static final Logger log = LoggerFactory.getLogger(ModelManager.class);

    private final AtomicReference<Recognizer> current = new AtomicReference<>();
    private final ExecutorService trainer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "model-trainer");
        t.setDaemon(true); return t; });

    private volatile Path facesRoot;
    private volatile Path modelDir;
    private final AttendanceProperties properties;

    public ModelManager(AttendanceProperties properties) {
        this.properties = properties;
        applyDirectories(properties.directories());
        properties.onChange(snapshot -> applyDirectories(snapshot.directories()));
    }

    public Recognizer getRecognizer() { return current.get(); }

    public boolean isReady() { return current.get() != null; }

    public void configure(Path facesRoot, Path modelDir) {
        if (facesRoot != null) this.facesRoot = facesRoot;
        if (modelDir != null) this.modelDir = modelDir;
    }

    /** Ensure a persisted model is loaded in the background if missing. */
    public CompletableFuture<Void> ensureLoadedAsync() {
        return CompletableFuture.runAsync(this::ensureLoadedSync, trainer);
    }

    /** Synchronous ensure-load executed on the current thread without training new weights. */
    private void ensureLoadedSync() {
        if (current.get() != null) {
            return;
        }
        if (!loadPersistedModel()) {
            log.info("No persisted LBPH model found in {}; skipping automatic training", modelDir);
        }
    }

    private boolean loadPersistedModel() {
        if (modelDir == null) {
            log.warn("Model directory not configured; cannot load persisted recognizer");
            return false;
        }
        Path modelFile = modelDir.resolve("lbph.yml");
        if (!Files.exists(modelFile)) {
            return false;
        }
        Recognizer recognizer = createPrimaryRecognizer();
        try {
            recognizer.loadModel(modelDir);
            current.set(recognizer);
            log.info("Loaded persisted LBPH model from {}", modelDir);
            return true;
        } catch (IOException ex) {
            log.error("Failed to load persisted LBPH model from {}: {}", modelDir, ex.toString());
            return false;
        }
    }

    /** Retrains from the full dataset in background and swaps in atomically. */
    public CompletableFuture<Void> retrainAllAsync() {
        return CompletableFuture.runAsync(this::retrainAllSync, trainer);
    }

    private FaceImageProcessor buildProcessor() {
        return new FaceImageProcessor(properties.preprocessing());
    }

    private Recognizer createPrimaryRecognizer() {
        return new LBPHRecognizer(buildProcessor()).configureFrom(properties);
    }

    /** Synchronous full retrain executed on the current thread (avoids deadlock). */
    private void retrainAllSync() {
        long t0 = System.nanoTime();
        log.info("Model full retrain: starting (facesRoot={})", facesRoot);
        try {
            if (Files.exists(modelDir)) {
                Files.walk(modelDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            }
            Files.createDirectories(modelDir);
        } catch (IOException e) {
            log.warn("Could not clear model directory {}: {}", modelDir, e.toString());
        }
        Recognizer r = createPrimaryRecognizer();
        boolean hasStudents = false;
        try (var paths = Files.list(facesRoot)) {
            hasStudents = paths.anyMatch(Files::isDirectory);
        } catch (IOException e) {
            log.warn("Could not list faces directory {}: {}", facesRoot, e.toString());
        }
        if (!hasStudents) {
            log.warn("No student directories found under {}; skipping training", facesRoot);
            try {
                r.saveModel(modelDir);
            } catch (IOException e) {
                log.warn("Could not save empty model to {}: {}", modelDir, e.toString());
            }
            current.set(r);
            return;
        }
        try {
            r.train(facesRoot);
            r.saveModel(modelDir);
            current.set(r);
            log.info("Model full retrain completed in {}ms", (System.nanoTime() - t0) / 1_000_000L);
        } catch (IOException e) {
            log.error("Full retrain failed: {}", e.toString());
        }
    }

    private void applyDirectories(AttendanceProperties.Directories directories) {
        if (directories == null) {
            return;
        }
        Path faces = directories.facesDir();
        Path models = directories.modelDir();
        if (faces != null) {
            this.facesRoot = faces;
        }
        if (models != null) {
            this.modelDir = models;
        }
    }

    /** Incrementally updates the model with a new student's images if supported; otherwise retrains. */
    public CompletableFuture<Void> updateStudentAsync(String studentId) {
        Objects.requireNonNull(studentId, "studentId");
        Path studentDir = facesRoot.resolve(studentId);
        return CompletableFuture.runAsync(() -> {
            long t0 = System.nanoTime();
            log.info("Model incremental update: starting for student={} (dir={})", studentId, studentDir);
            Recognizer r = current.get();
            if (r == null) {
                // Avoid scheduling ensureLoaded on the same single-thread executor which would deadlock
                ensureLoadedSync();
                r = current.get();
            }
            try {
                if (r != null && r.supportsIncremental()) {
                    r.updateIncremental(studentDir, studentId);
                    r.saveModel(modelDir);
                    log.info("Model incremental update completed for {} in {}ms", studentId, (System.nanoTime() - t0) / 1_000_000L);
                } else {
                    log.info("Incremental not supported; retraining full model synchronously...");
                    retrainAllSync();
                    log.info("Model full retrain after incremental fallback completed in {}ms", (System.nanoTime() - t0) / 1_000_000L);
                }
            } catch (IOException e) {
                log.error("Update failed for {}: {}", studentId, e.toString());
            }
        }, trainer);
    }

    /** Incrementally removes a student from the model if supported; otherwise retrains. */
    public CompletableFuture<Void> removeStudentAsync(String studentId) {
        Objects.requireNonNull(studentId, "studentId");
        return CompletableFuture.runAsync(() -> {
            long t0 = System.nanoTime();
            log.info("Model incremental removal: starting for student={}", studentId);
            Recognizer r = current.get();
            if (r == null) {
                ensureLoadedSync();
                r = current.get();
            }
            try {
                if (r != null) {
                    r.removeStudent(studentId);
                    r.saveModel(modelDir);
                    log.info("Model incremental removal completed for {} in {}ms", studentId,
                            (System.nanoTime() - t0) / 1_000_000L);
                }
            } catch (UnsupportedOperationException e) {
                log.info("Incremental removal not supported; retraining full model synchronously...");
                retrainAllSync();
                log.info("Model full retrain after removal fallback completed in {}ms",
                        (System.nanoTime() - t0) / 1_000_000L);
            } catch (IOException e) {
                log.error("Removal failed for {}: {}", studentId, e.toString());
            }
        }, trainer);
    }

    /** Convenience: full retrain and wait, swallowing any errors. */
    public void retrainAllQuietly() {
        try { retrainAllAsync().join(); } catch (RuntimeException ignored) {}
    }

    /** Convenience: update a single student and wait, swallowing any errors. */
    public void updateStudentQuietly(String studentId) {
        try { updateStudentAsync(studentId).join(); } catch (RuntimeException ignored) {}
    }

    /** Convenience: remove a single student and wait, swallowing any errors. */
    public void removeStudentQuietly(String studentId) {
        try { removeStudentAsync(studentId).join(); } catch (RuntimeException ignored) {}
    }

    /**
     * Creates a temporary dataset that mirrors the current enrolled faces but swaps the
     * target student's images with the provided training captures. The resulting
     * directory can be used to validate a recognizer without mutating the live dataset.
     */
    public CandidateDataset assembleCandidateDataset(UUID studentId, List<Path> trainingImages) throws IOException {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(trainingImages, "trainingImages");
        Path candidateRoot = Files.createTempDirectory(properties.directories().dataDir(), "faces-candidate-");
        Path studentDirName = facesRoot.resolve(studentId.toString());
        Files.walkFileTree(facesRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(studentDirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path relative = facesRoot.relativize(dir);
                Path target = candidateRoot.resolve(relative);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isRegularFile(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = facesRoot.relativize(file);
                if (relative.startsWith(studentId.toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = candidateRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        Path candidateStudentDir = candidateRoot.resolve(studentId.toString());
        Files.createDirectories(candidateStudentDir);
        for (Path image : trainingImages) {
            if (image == null) {
                continue;
            }
            Path target = candidateStudentDir.resolve(image.getFileName());
            Files.copy(image, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Map<String, Object> stats = collectDatasetStats(candidateRoot);
        return new CandidateDataset(candidateRoot, stats);
    }

    /** Builds aggregate statistics for a dataset rooted at the given path. */
    public Map<String, Object> collectDatasetStats(Path root) {
        Map<String, Object> stats = new HashMap<>();
        Map<String, Long> perStudent = new HashMap<>();
        long totalImages = 0;
        int students = 0;
        if (root == null) {
            stats.put("students", 0);
            stats.put("images", 0L);
            stats.put("images_per_student", perStudent);
            stats.put("root", null);
            return stats;
        }
        try (var stream = Files.list(root)) {
            for (Path dir : stream.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                students++;
                long count = 0;
                try (var files = Files.list(dir)) {
                    count = files.filter(Files::isRegularFile).count();
                } catch (IOException ex) {
                    log.warn("Failed to inspect {}: {}", dir, ex.getMessage());
                }
                perStudent.put(dir.getFileName().toString(), count);
                totalImages += count;
            }
        } catch (IOException ex) {
            log.warn("Failed to list dataset {}: {}", root, ex.getMessage());
        }
        stats.put("root", root.toAbsolutePath().toString());
        stats.put("students", students);
        stats.put("images", totalImages);
        stats.put("images_per_student", perStudent);
        stats.put("generated_at", java.time.OffsetDateTime.now().toString());
        return stats;
    }

    /** Serializes the current trained model directory into a ZIP archive. */
    public byte[] exportModelArchive() throws IOException {
        if (modelDir == null || !Files.exists(modelDir)) {
            return new byte[0];
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walkFileTree(modelDir, new SimpleFileVisitor<>() {
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

    /**
     * Trains a throwaway recognizer on the provided dataset without mutating the
     * live model. Useful for validation runs.
     */
    public Recognizer trainTemporary(Path datasetRoot) throws IOException {
        Objects.requireNonNull(datasetRoot, "datasetRoot");
        Recognizer recognizer = createPrimaryRecognizer();
        recognizer.train(datasetRoot);
        return recognizer;
    }

    /** Creates a new recognizer instance configured with the current properties. */
    public Recognizer createRecognizer() {
        return createPrimaryRecognizer();
    }

    /** Shutdown the background trainer executor gracefully. */
    public void shutdown() {
        trainer.shutdown();
        try {
            if (!trainer.awaitTermination(5, TimeUnit.SECONDS)) {
                trainer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            trainer.shutdownNow();
        }
    }

    @PreDestroy
    public void onDestroy() {
        shutdown();
    }

    public record CandidateDataset(Path root, Map<String, Object> stats) {}
}
