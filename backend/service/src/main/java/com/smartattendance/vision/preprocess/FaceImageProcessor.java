package com.smartattendance.vision.preprocess;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.smartattendance.config.AttendanceProperties;
import com.smartattendance.util.OpenCVLoader;
import com.smartattendance.vision.HaarFaceDetector;

/**
 * Crops and normalizes captured face images using the shared preprocessing pipeline.
 */
public class FaceImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(FaceImageProcessor.class);

    private static final double PADDING_RATIO = 0.05d;

    private final Object preprocessLock = new Object();
    private volatile List<Preprocessor> preprocessors;
    private final boolean customPreprocessors;
    private volatile int targetWidth;
    private volatile int targetHeight;
    private final HaarFaceDetector detector;
    private final AtomicBoolean openCvLoaded = new AtomicBoolean(false);

    public FaceImageProcessor() {
        this(new AttendanceProperties().preprocessing(), null, null);
    }

    public FaceImageProcessor(List<Preprocessor> preprocessors) {
        this(new AttendanceProperties().preprocessing(), null, preprocessors);
    }

    public FaceImageProcessor(HaarFaceDetector detector) {
        this(new AttendanceProperties().preprocessing(), detector, null);
    }

    public FaceImageProcessor(HaarFaceDetector detector, List<Preprocessor> preprocessors) {
        this(new AttendanceProperties().preprocessing(), detector, preprocessors);
    }

    public FaceImageProcessor(AttendanceProperties.Preprocessing config) {
        this(config, null, null);
    }

    public FaceImageProcessor(AttendanceProperties.Preprocessing config, HaarFaceDetector detector) {
        this(config, detector, null);
    }

    public FaceImageProcessor(AttendanceProperties.Preprocessing config, List<Preprocessor> preprocessors) {
        this(config, null, preprocessors);
    }

    public FaceImageProcessor(AttendanceProperties.Preprocessing config,
                              HaarFaceDetector detector,
                              List<Preprocessor> preprocessors) {
        Objects.requireNonNull(config, "preprocessing config is required");
        this.detector = detector;
        applyTargetSize(config);
        if (preprocessors == null || preprocessors.isEmpty()) {
            this.customPreprocessors = false;
            this.preprocessors = buildDefaultPreprocessors();
        } else {
            this.customPreprocessors = true;
            this.preprocessors = new ArrayList<>(preprocessors);
        }
    }

    public ProcessedImage process(byte[] data, FaceImageProcessingOptions options) {
        Objects.requireNonNull(data, "image data is required");
        ensureOpenCvLoaded();

        MatOfByte buffer = new MatOfByte(data);
        Mat image = null;
        Mat working = null;
        Mat processed = null;
        MatOfByte encoded = null;
        try {
            image = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IllegalArgumentException("Unable to decode supplied face image");
            }

            boolean alreadyTarget = isTargetSize(image);
            if (alreadyTarget) {
                working = image.clone();
            } else {
                working = crop(image, options);
                if (working == null || working.empty()) {
                    working = image.clone();
                }
            }
            processed = applyPreprocessors(working, alreadyTarget);
            Mat target = processed != null && !processed.empty() ? processed : working;

            encoded = new MatOfByte();
            MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 90);
            try {
                Imgcodecs.imencode(".jpg", target, encoded, params);
            } finally {
                try { params.release(); } catch (Exception ignored) {}
            }
            byte[] output = encoded.toArray();
            return new ProcessedImage(output, MediaType.IMAGE_JPEG);
        } finally {
            if (encoded != null) {
                try { encoded.release(); } catch (Exception ignored) {}
            }
            if (processed != null && processed != working) {
                try { processed.release(); } catch (Exception ignored) {}
            }
            if (working != null) {
                try { working.release(); } catch (Exception ignored) {}
            }
            if (image != null) {
                try { image.release(); } catch (Exception ignored) {}
            }
            try { buffer.release(); } catch (Exception ignored) {}
        }
    }

    public Mat preprocess(Mat image) {
        Objects.requireNonNull(image, "image is required");
        ensureOpenCvLoaded();

        boolean alreadyTarget = isTargetSize(image);
        Mat working = image.clone();
        Mat result = null;
        boolean success = false;
        try {
            result = applyPreprocessors(working, alreadyTarget);
            if (result == null || result.empty()) {
                result = working;
            } else if (result != working) {
                try { working.release(); } catch (Exception ignored) {}
            }
            success = true;
            return result;
        } finally {
            if (!success) {
                if (result != null && result != working) {
                    try { result.release(); } catch (Exception ignored) {}
                }
                try { working.release(); } catch (Exception ignored) {}
            }
        }
    }

    public void updatePreprocessingConfig(AttendanceProperties.Preprocessing config) {
        if (config == null) {
            return;
        }
        synchronized (preprocessLock) {
            applyTargetSize(config);
            if (!customPreprocessors) {
                preprocessors = buildDefaultPreprocessors();
            }
        }
    }

    private void applyTargetSize(AttendanceProperties.Preprocessing config) {
        int width = sanitizeSize(config.width(), config.height());
        int height = sanitizeSize(config.height(), config.width());
        this.targetWidth = width;
        this.targetHeight = height;
    }

    private int sanitizeSize(int primary, int alternate) {
        int value = primary > 0 ? primary : alternate;
        if (value <= 0) {
            value = 1;
        }
        return value;
    }

    private List<Preprocessor> buildDefaultPreprocessors() {
        List<Preprocessor> steps = new ArrayList<>();
        steps.add(new GrayscaleProcessor());
        steps.add(new ClaheProcessor());
        steps.add(new Normalizer(targetWidth, targetHeight));
        return steps;
    }

    private Mat applyPreprocessors(Mat working, boolean skipClahe) {
        if (working == null || working.empty()) {
            return working;
        }
        Mat current = working;
        List<Preprocessor> steps = preprocessors;
        for (Preprocessor preprocessor : steps) {
            if (preprocessor == null) {
                continue;
            }
            if (skipClahe && preprocessor instanceof ClaheProcessor) {
                continue;
            }
            Mat next = null;
            try {
                next = preprocessor.process(current);
            } catch (RuntimeException ex) {
                log.debug("Preprocessor {} failed: {}", preprocessor.getClass().getSimpleName(), ex.getMessage());
            }
            if (next == null || next.empty()) {
                continue;
            }
            if (current != working && next != current) {
                try { current.release(); } catch (Exception ignored) {}
            }
            current = next;
        }
        return current;
    }

    private void ensureOpenCvLoaded() {
        if (openCvLoaded.compareAndSet(false, true)) {
            boolean loaded = OpenCVLoader.loadOrWarn();
            if (!loaded) {
                openCvLoaded.set(false);
                throw new IllegalStateException("OpenCV native library unavailable");
            }
        }
    }

    private Mat crop(Mat source, FaceImageProcessingOptions options) {
        if (source == null || source.empty()) {
            return source;
        }
        int width = source.cols();
        int height = source.rows();
        if (width <= 0 || height <= 0) {
            return source;
        }
        if (options != null && options.hasBoundingBox()) {
            double frameWidth = options.getFrameWidth();
            double frameHeight = options.getFrameHeight();
            if (frameWidth > 0 && frameHeight > 0) {
                double scaleX = width / frameWidth;
                double scaleY = height / frameHeight;
                double faceWidth = options.getBoxWidth() * scaleX;
                double faceHeight = options.getBoxHeight() * scaleY;
                if (faceWidth > 0 && faceHeight > 0) {
                    double faceX = Math.max(0.0d, options.getBoxX() * scaleX);
                    double faceY = Math.max(0.0d, options.getBoxY() * scaleY);
                    Mat cropped = cropUsingBounds(source, faceX, faceY, faceWidth, faceHeight);
                    if (cropped != null) {
                        return cropped;
                    }
                }
            }
        }

        Rectangle detected = detectPrimaryFace(source);
        if (detected != null) {
            Mat cropped = cropUsingBounds(source, detected.x, detected.y, detected.width, detected.height);
            if (cropped != null) {
                return cropped;
            }
        }

        return centerCrop(source);
    }

    private Rectangle detectPrimaryFace(Mat source) {
        if (detector == null) {
            return null;
        }
        try {
            List<Rectangle> faces = detector.detect(source);
            if (faces == null || faces.isEmpty()) {
                return null;
            }
            return faces.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(rect -> rect.width * rect.height))
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.debug("Face detection during preprocessing failed: {}", ex.getMessage());
            return null;
        }
    }

    private Mat cropUsingBounds(Mat source, double faceX, double faceY, double faceWidth, double faceHeight) {
        int width = source.cols();
        int height = source.rows();
        if (faceWidth <= 0 || faceHeight <= 0 || width <= 0 || height <= 0) {
            return null;
        }

        double faceSize = Math.max(faceWidth, faceHeight);
        double padding = faceSize * PADDING_RATIO;
        double desiredSize = Math.min(
                Math.max(faceSize + padding * 2.0d, targetEdge()),
                Math.min(width, height));
        int cropEdge = (int) Math.round(desiredSize);
        cropEdge = Math.max(1, Math.min(cropEdge, Math.min(width, height)));

        double centerX = faceX + faceWidth / 2.0d;
        double centerY = faceY + faceHeight / 2.0d;
        int cropX = (int) Math.round(centerX - cropEdge / 2.0d);
        int cropY = (int) Math.round(centerY - cropEdge / 2.0d);
        cropX = Math.max(0, Math.min(cropX, width - cropEdge));
        cropY = Math.max(0, Math.min(cropY, height - cropEdge));

        if (cropEdge <= 0 || cropX + cropEdge > width || cropY + cropEdge > height) {
            return null;
        }
        Rect rect = new Rect(cropX, cropY, cropEdge, cropEdge);
        return new Mat(source, rect).clone();
    }

    private int targetEdge() {
        return Math.max(targetWidth, targetHeight);
    }

    private boolean isTargetSize(Mat mat) {
        return mat != null && mat.cols() == targetWidth && mat.rows() == targetHeight;
    }

    private Mat centerCrop(Mat source) {
        int width = source.cols();
        int height = source.rows();
        if (width == height) {
            return source.clone();
        }
        int size = Math.min(width, height);
        int x = Math.max(0, (width - size) / 2);
        int y = Math.max(0, (height - size) / 2);
        Rect rect = new Rect(x, y, size, size);
        return new Mat(source, rect).clone();
    }

    public record ProcessedImage(byte[] data, MediaType mediaType) {
    }
}
