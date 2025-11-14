package com.smartattendance.vision;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smartattendance.config.AttendanceProperties;

/** Face detector based on OpenCV's Haar cascade classifier. */
public class HaarFaceDetector {
    private static final Logger log = LoggerFactory.getLogger(HaarFaceDetector.class);

    private final CascadeClassifier classifier;
    private final CascadeClassifier fallback;
    private final AttendanceProperties config;

    public HaarFaceDetector(String cascadePath, AttendanceProperties config) {
        try {
            Loader.load(opencv_java.class);
        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load OpenCV native library: {}", e.toString());
            throw e;
        }

        this.config = config;
        Path primaryPath = cascadePath != null && !cascadePath.isBlank() ? Paths.get(cascadePath) : null;
        this.classifier = initializeClassifier(primaryPath, "haarcascade_frontalface_alt_tree.xml", "primary");

        Path fallbackPath = null;
        if (config != null) {
            fallbackPath = config.directories().modelDir().resolve("haarcascade_frontalface_default.xml");
        }
        this.fallback = initializeClassifier(fallbackPath, "haarcascade_frontalface_default.xml", "fallback");
    }

    public HaarFaceDetector(String cascadePath) {
        this(cascadePath, new AttendanceProperties());
    }

    private CascadeClassifier initializeClassifier(Path filePath, String resourceName, String kind) {
        CascadeClassifier classifier = new CascadeClassifier();
        boolean loaded = false;

        if (filePath != null) {
            try {
                if (Files.exists(filePath)) {
                    loaded = classifier.load(filePath.toString());
                    if (!loaded) {
                        log.warn("Failed to load {} cascade from {}", kind, filePath);
                    }
                } else {
                    log.debug("{} cascade file missing at {}", kind, filePath);
                }
            } catch (Exception ex) {
                log.warn("Error loading {} cascade from {}: {}", kind, filePath, ex.toString());
            }
        }

        if (!loaded) {
            String resourcePath = "/vision/models/" + resourceName;
            try (InputStream in = HaarFaceDetector.class.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    Path temp = Files.createTempFile("cascade-", ".xml");
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    temp.toFile().deleteOnExit();
                    loaded = classifier.load(temp.toString());
                    if (!loaded) {
                        log.warn("Failed to load {} cascade from classpath resource {}", kind, resourcePath);
                    } else {
                        log.info("Loaded {} cascade from classpath resource {}", kind, resourcePath);
                    }
                } else {
                    log.warn("Classpath resource {} not found for {} cascade", resourcePath, kind);
                }
            } catch (IOException ex) {
                log.warn("Error provisioning {} cascade from classpath resource {}: {}", kind, resourcePath, ex.toString());
            }
        }

        if (!loaded) {
            log.error("Face detection {} cascade could not be loaded; detections will be unavailable.", kind);
        }

        return classifier;
    }

    /** Detects faces in the given image with basic preprocessing and tuned params. */
    public List<Rectangle> detect(Mat mat) {
        Mat gray = new Mat();
        MatOfRect rects = new MatOfRect();
        Mat detectionMat = null;
        try {
            if (mat.channels() == 1) {
                mat.copyTo(gray);
            } else {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            }
            CLAHE clahe = Imgproc.createCLAHE();
            clahe.apply(gray, gray);

            double scaleFactorConfig = resolveDetectionScale();
            if (scaleFactorConfig != 1.0d) {
                detectionMat = new Mat();
                int interpolation = scaleFactorConfig < 1.0d ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR;
                Imgproc.resize(gray, detectionMat, new Size(), scaleFactorConfig, scaleFactorConfig, interpolation);
            } else {
                detectionMat = gray;
            }

            int min = Math.max(config.detection().minFace(), config.live().recognition().minFace());
            min = Math.max(min, Math.min(mat.cols(), mat.rows()) / 8);
            int scaledMin = (int)Math.max(24, Math.round(min * scaleFactorConfig));
            Size minSize = new Size(scaledMin, scaledMin);
            double scaleFactor = config.detection().cascadeScaleFactor();
            int minNeighbors = config.detection().cascadeMinNeighbors();
            // Primary detector (slightly tuned via config). Guard against missing cascade.
            if (!classifier.empty()) {
                try {
                    classifier.detectMultiScale(detectionMat, rects, scaleFactor, minNeighbors, 0, minSize, new Size());
                } catch (CvException e) {
                    log.warn("Primary cascade failed: {}", e.getMessage());
                }
            }
            if ((rects.empty() || classifier.empty()) && !fallback.empty()) {
                // Fallback (significantly more permissive to recover obvious faces)
                int fallbackNeighbors = Math.max(2, minNeighbors - 2);
                try {
                    fallback.detectMultiScale(detectionMat, rects, scaleFactor, fallbackNeighbors, 0, minSize, new Size());
                } catch (CvException e) {
                    log.warn("Fallback cascade failed: {}", e.getMessage());
                }
            }

            Rect[] arr = rects.toArray();
            List<Rectangle> raw = new ArrayList<>(arr.length);
            for (Rect r : arr) {
                int x = r.x;
                int y = r.y;
                int w = r.width;
                int h = r.height;
                if (scaleFactorConfig != 1.0d) {
                    double inv = scaleFactorConfig != 0.0d ? (1.0d / scaleFactorConfig) : 1.0d;
                    x = (int)Math.round(r.x * inv);
                    y = (int)Math.round(r.y * inv);
                    w = (int)Math.round(r.width * inv);
                    h = (int)Math.round(r.height * inv);
                }
                raw.add(new Rectangle(x, y, Math.max(1, w), Math.max(1, h)));
            }
            // Non-maximum suppression to reduce duplicates
            return nonMaxSuppression(raw, 0.35);
        } finally {
            try { gray.release(); } catch (Exception ignored) {}
            try { rects.release(); } catch (Exception ignored) {}
            if (detectionMat != null && detectionMat != gray) {
                try { detectionMat.release(); } catch (Exception ignored) {}
            }
        }
    }

    private double resolveDetectionScale() {
        double scale = config.detection().downscale();
        if (!Double.isFinite(scale) || scale <= 0.0d) {
            scale = 1.0d;
        }
        double preprocScale = config.preprocessing().detectionScale();
        if (Double.isFinite(preprocScale) && preprocScale > 0.0d) {
            scale = preprocScale;
        }
        return scale;
    }

    private static List<Rectangle> nonMaxSuppression(List<Rectangle> boxes, double iouThreshold) {
        if (boxes == null || boxes.isEmpty()) return List.of();
        boxes = new ArrayList<>(boxes);
        boxes.sort((a,b) -> Integer.compare(b.width*b.height, a.width*a.height));
        List<Rectangle> out = new ArrayList<>();
        boolean[] suppressed = new boolean[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) continue;
            Rectangle bi = boxes.get(i);
            out.add(bi);
            for (int j = i+1; j < boxes.size(); j++) {
                if (suppressed[j]) continue;
                Rectangle bj = boxes.get(j);
                if (iou(bi, bj) > iouThreshold) suppressed[j] = true;
            }
        }
        return out;
    }

    private static double iou(Rectangle a, Rectangle b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        int iw = Math.max(0, x2 - x1);
        int ih = Math.max(0, y2 - y1);
        double inter = (double) iw * ih;
        double union = (double) a.width * a.height + (double) b.width * b.height - inter;
        if (union <= 0.0) return 0.0;
        return inter / union;
    }
}
