package com.smartattendance.vision.recognizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.face.LBPHFaceRecognizer;
import org.opencv.imgcodecs.Imgcodecs;

import com.smartattendance.vision.Recognizer;
import com.smartattendance.vision.preprocess.FaceImageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smartattendance.config.AttendanceProperties;
import com.smartattendance.vision.preprocess.ImageQuality;

/**
 * LBPH-based recognizer with incremental update support.
 */
public class LBPHRecognizer implements Recognizer {
    private static final Logger log = LoggerFactory.getLogger(LBPHRecognizer.class);
    private LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
    private final Map<Integer, String> labels = new HashMap<>();
    private final Map<String, Integer> reverse = new HashMap<>();
    private final FaceImageProcessor processor;
    private boolean trained = false;
    private AttendanceProperties properties;
    private Path trainingRoot;

    public LBPHRecognizer(FaceImageProcessor processor) {
        this.processor = processor != null ? processor : new FaceImageProcessor();
    }

    public LBPHRecognizer(List<com.smartattendance.vision.preprocess.Preprocessor> preprocessors) {
        this(new FaceImageProcessor(preprocessors));
    }

    public LBPHRecognizer() { this(new FaceImageProcessor()); }

    public LBPHRecognizer(AttendanceProperties properties) {
        this(properties != null
                ? new FaceImageProcessor(properties.preprocessing())
                : new FaceImageProcessor());
        configureFrom(properties);
    }

    /** Optionally configure LBPH parameters from Configuration. */
    public LBPHRecognizer configureFrom(AttendanceProperties properties) {
        if (properties == null) return this;
        this.properties = properties;
        try {
            processor.updatePreprocessingConfig(properties.preprocessing());
        } catch (Exception ex) {
            log.warn("Failed to apply preprocessing config: {}", ex.getMessage());
        }
        try {
            AttendanceProperties.Recognition recognition = properties.recognition();
            AttendanceProperties.Lbph lbph = recognition != null ? recognition.lbph() : null;
            try {
                int radius = lbph != null ? lbph.radius() : 1;
                int neighbors = lbph != null ? lbph.neighbors() : 8;
                int gridX = lbph != null ? lbph.gridX() : 8;
                int gridY = lbph != null ? lbph.gridY() : 8;
                this.recognizer = LBPHFaceRecognizer.create(radius, neighbors, gridX, gridY, Double.MAX_VALUE);
                if (lbph != null) {
                    log.info("Configured LBPH with radius={}, neighbors={}, gridX={}, gridY={} from properties",
                            radius, neighbors, gridX, gridY);
                }
            } catch (Exception t) {
                log.warn("Failed to apply LBPH configuration; falling back to OpenCV defaults: {}", t.toString());
                this.recognizer = LBPHFaceRecognizer.create();
            }
        } catch (Exception ex) {
            log.warn("Unable to configure LBPH recognizer from properties: {}", ex.toString());
        }
        return this;
    }

    private Mat apply(Mat image) {
        return processor.preprocess(image);
    }

    @Override
    public void train(Path root) throws IOException {
        long t0 = System.nanoTime();
        trained = false;
        labels.clear();
        reverse.clear();
        this.trainingRoot = root;
        double blurThreshold = trainingBlurThreshold();
        if (!Files.isDirectory(root)) {
            log.error("Training directory not found: {}", root.toAbsolutePath());
            return;
        }
        
        // Memory-efficient training: process students in batches
        List<Mat> batchImages = new ArrayList<>();
        List<Integer> batchIds = new ArrayList<>();
        boolean baseModelTrained = false;
        int label = 0;
        int totalProcessed = 0;
        int batchSize = 10; // Process 10 images at a time to limit memory usage
        
        for (Path studentDir : Files.newDirectoryStream(root)) {
            if (!Files.isDirectory(studentDir)) continue;
            String studentId = studentDir.getFileName().toString();
            labels.put(label, studentId);
            reverse.put(studentId, label);
            
            List<Path> imageFiles = new ArrayList<>();
            try (var stream = Files.newDirectoryStream(studentDir, p -> {
                String s = p.toString().toLowerCase();
                return s.endsWith(".png") || s.endsWith(".jpg");
            })) {
                for (Path img : stream) {
                    imageFiles.add(img);
                }
            }
            
            // Process images in smaller batches to avoid memory issues
            for (int batchStart = 0; batchStart < imageFiles.size(); batchStart += batchSize) {
                int endIndex = Math.min(batchStart + batchSize, imageFiles.size());
                List<Path> batch = imageFiles.subList(batchStart, endIndex);
                
                for (Path img : batch) {
                    Mat m = Imgcodecs.imread(img.toString());
                    if (m.empty()) continue;
                    if (!ImageQuality.isSharpEnough(m, blurThreshold)) {
                        log.debug("Skipping blurred training image {} variance below {}", img, blurThreshold);
                        try { m.release(); } catch (Exception ignored) {}
                        continue;
                    }
                    
                    // Process augmentations with memory management
                    java.util.List<Mat> variants = com.smartattendance.vision.preprocess.Augmenter.augment(m, properties);
                    
                    // Limit augmentations to reduce memory usage during training
                    int maxVariants = Math.min(variants.size(), 2); // Only use original + 1 augmentation
                    
                    for (int variantIndex = 0; variantIndex < maxVariants; variantIndex++) {
                        Mat v = variants.get(variantIndex);
                        Mat pv = apply(v);
                        batchImages.add(pv);
                        batchIds.add(label);
                        totalProcessed++;
                        
                        // Release variant immediately after processing
                        try { v.release(); } catch (Exception ignored) {}
                        
                        // If batch gets too large, train incrementally
                        if (batchImages.size() >= 30) { // Reduced batch size for memory efficiency
                            baseModelTrained = trainBatch(batchImages, batchIds, baseModelTrained);
                        }
                    }
                    
                    // Release any unused variants
                    for (int j = maxVariants; j < variants.size(); j++) {
                        try { variants.get(j).release(); } catch (Exception ignored) {}
                    }
                    try { m.release(); } catch (Exception ignored) {}
                }
            }
            label++;
        }

        // Train with any remaining images
        if (!batchImages.isEmpty()) {
            baseModelTrained = trainBatch(batchImages, batchIds, baseModelTrained);
        }

        if (totalProcessed < 2) {
            log.error("Insufficient training data; recognizer disabled.");
            return;
        }
        
        trained = true;
        long dtMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("LBPH trained: images={} labels={} took={}ms", totalProcessed, labels.size(), dtMs);
    }
    
    private boolean trainBatch(List<Mat> images, List<Integer> ids, boolean baseModelTrained) {
        if (images.isEmpty()) return baseModelTrained;

        MatOfInt matIds = new MatOfInt();
        try {
            matIds.fromList(ids);
            if (!baseModelTrained) {
                recognizer.train(images, matIds);
                log.debug("Trained base batch of {} images", images.size());
                baseModelTrained = true;
            } else {
                recognizer.update(images, matIds);
                log.debug("Updated recognizer with batch of {} images", images.size());
            }
        } finally {
            try { matIds.release(); } catch (Exception ignored) {}
            for (Mat img : images) { 
                try { img.release(); } catch (Exception ignored) {} 
            }
            images.clear();
            ids.clear();
        }
        return baseModelTrained;
    }

    @Override
    public boolean supportsIncremental() { return true; }

    @Override
    public void updateIncremental(Path studentDir, String studentId) throws IOException {
        long t0 = System.nanoTime();
        if (!trained) {
            // if not trained yet, fallback to full training on root dir
            Path root = studentDir.getParent();
            if (root != null) train(root);
            return;
        }
        if (trainingRoot == null) {
            Path parent = studentDir.getParent();
            if (parent != null) trainingRoot = parent;
        }
        int label = reverse.computeIfAbsent(studentId, id -> {
            int next = labels.size();
            labels.put(next, id);
            return next;
        });
        List<Mat> images = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        if (!Files.isDirectory(studentDir)) return;
        double blurThreshold = trainingBlurThreshold();
        for (Path img : Files.newDirectoryStream(studentDir, p -> {
            String s = p.toString().toLowerCase();
            return s.endsWith(".png") || s.endsWith(".jpg");
        })) {
            Mat m = Imgcodecs.imread(img.toString());
            if (m.empty()) continue;
            if (!ImageQuality.isSharpEnough(m, blurThreshold)) {
                log.debug("Skipping blurred incremental image {} variance below {}", img, blurThreshold);
                try { m.release(); } catch (Exception ignored) {}
                continue;
            }
            java.util.List<Mat> variants = com.smartattendance.vision.preprocess.Augmenter.augment(m, properties);
            for (Mat v : variants) {
                Mat pv = apply(v);
                images.add(pv);
                ids.add(label);
                try { v.release(); } catch (Exception ignored) {}
            }
            try { m.release(); } catch (Exception ignored) {}
        }
        if (images.isEmpty()) return;
        MatOfInt matIds = new MatOfInt();
        try {
            matIds.fromList(ids);
            recognizer.update(images, matIds);
            trained = true;
            long dtMs = (System.nanoTime() - t0) / 1_000_000L;
            log.info("LBPH incremental update: student={} imagesAdded={} newLabel={} took={}ms",
                    studentId, images.size(), label, dtMs);
        } finally {
            try { matIds.release(); } catch (Exception ignored) {}
            for (Mat img : images) { try { img.release(); } catch (Exception ignored) {} }
        }
    }

    @Override
    public void removeStudent(String studentId) throws IOException {
        Integer label = reverse.remove(studentId);
        if (label == null) return;
        labels.remove(label);
        // Rebuild model without the removed student
        if (trainingRoot != null) {
            recognizer = LBPHFaceRecognizer.create();
            configureFrom(properties);
            train(trainingRoot);
        } else {
            trained = false;
        }
    }

    @Override
    public Prediction recognize(Mat face) {
        if (!trained) return new Prediction("unknown", Double.POSITIVE_INFINITY);
        Mat processed = apply(face);
        int[] label = new int[1];
        double[] conf = new double[1];
        try {
            recognizer.predict(processed, label, conf);
        } finally {
            if (processed != face) {
                try { processed.release(); } catch (Exception ignored) {}
            }
        }
        String id = labels.getOrDefault(label[0], "unknown");
        // OpenCV LBPH returns a distance (lower = better). Return this raw distance.
        double distance = Math.max(0.0, conf[0]);
        return new Prediction(id, distance);
    }

    private double trainingBlurThreshold() {
        if (properties == null || properties.capture() == null) {
            return 0.0d;
        }
        double post = properties.capture().postCaptureBlurVarianceThreshold();
        if (Double.isFinite(post) && post > 0.0d) {
            return post;
        }
        double pre = properties.capture().blurVarianceThreshold();
        if (Double.isFinite(pre) && pre > 0.0d) {
            return pre;
        }
        return 0.0d;
    }

    @Override
    public void saveModel(Path modelDir) throws IOException {
        long t0 = System.nanoTime();
        Files.createDirectories(modelDir);
        Path model = modelDir.resolve("lbph.yml");
        recognizer.write(model.toString());
        // save labels mapping as simple text file
        Path labelsFile = modelDir.resolve("labels.txt");
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, String> e : labels.entrySet()) {
            lines.add(e.getKey() + "," + e.getValue());
        }
        Files.write(labelsFile, lines, StandardCharsets.UTF_8);
        long dtMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("LBPH model saved to {} in {}ms", modelDir.toString(), dtMs);
    }

    @Override
    public void loadModel(Path modelDir) throws IOException {
        long t0 = System.nanoTime();
        Path model = modelDir.resolve("lbph.yml");
        Path labelsFile = modelDir.resolve("labels.txt");
        if (!Files.exists(model) || !Files.exists(labelsFile)) return;
        recognizer.read(model.toString());
        labels.clear(); reverse.clear();
        for (String line : Files.readAllLines(labelsFile, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split(",", 2);
            if (parts.length != 2) continue;
            int k = Integer.parseInt(parts[0].trim());
            String v = parts[1].trim();
            labels.put(k, v);
            reverse.put(v, k);
        }
        trained = true;
        long dtMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("LBPH model loaded from {} (labels={}) in {}ms", modelDir.toString(), labels.size(), dtMs);
    }
}
