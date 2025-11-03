package com.smartattendance.supabase.service.face;

import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.smartattendance.supabase.dto.FaceCaptureAnalysisRequest;
import com.smartattendance.supabase.dto.FaceCaptureAnalysisResponse;
import com.smartattendance.util.OpenCVLoader;
import com.smartattendance.vision.HaarFaceDetector;
import com.smartattendance.vision.preprocess.ImageQuality;

@Service
public class FaceCaptureAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FaceCaptureAnalysisService.class);

    private final HaarFaceDetector detector;
    private final AtomicBoolean openCvLoaded = new AtomicBoolean(false);

    public FaceCaptureAnalysisService(HaarFaceDetector detector) {
        this.detector = detector;
    }

    public FaceCaptureAnalysisResponse analyse(FaceCaptureAnalysisRequest request) {
        if (request == null || !StringUtils.hasText(request.getImageData())) {
            throw new IllegalArgumentException("image_data is required");
        }
        try {
            ensureOpenCvLoaded();
        } catch (IllegalStateException ex) {
            log.warn("OpenCV unavailable, defaulting to pass-through analysis: {}", ex.getMessage());
            return fallbackResponse("Detector unavailable. Please try again.");
        }

        byte[] imageBytes = decodeImage(request.getImageData());
        if (imageBytes.length == 0) {
            throw new IllegalArgumentException("image_data contained no bytes");
        }

        MatOfByte buffer = null;
        Mat image = null;
        double brightness = 0.0d;
        double sharpness = 0.0d;
        try {
            buffer = new MatOfByte(imageBytes);
            image = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IllegalArgumentException("Unable to decode supplied image_data");
            }

            brightness = estimateBrightness(image);
            try {
                sharpness = ImageQuality.laplacianVariance(image);
            } catch (RuntimeException ex) {
                log.debug("Sharpness calculation failed: {}", ex.getMessage());
                sharpness = 0.0d;
            }

            List<Rectangle> faces;
            try {
                faces = detector.detect(image);
            } catch (RuntimeException ex) {
                log.warn("Face detection failed, defaulting to pass-through capture: {}", ex.getMessage());
                return fallbackResponse("Detector unavailable. Please try again.", sharpness, brightness);
            }
            int faceCount = faces != null ? faces.size() : 0;
            log.debug("Face capture analysis detected {} face(s) [sharpness={}, brightness={}]", faceCount, sharpness, brightness);

            List<FaceCaptureAnalysisResponse.BoundingBox> boxes = faces == null ? List.of()
                    : faces.stream()
                            .filter(rect -> rect != null)
                            .map(this::toBoundingBox)
                            .collect(Collectors.toList());

            FaceCaptureAnalysisResponse response = new FaceCaptureAnalysisResponse();
            response.setFaceCount(faceCount);
            response.setBoundingBoxes(boxes);
            response.setValid(faceCount == 1 && boxes.size() == 1);
            response.setSharpness(sharpness);
            response.setBrightness(brightness);
            response.setMessage(resolveMessage(faceCount, boxes.size()));
            return response;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Face capture analysis encountered an unexpected error: {}", ex.getMessage());
            return fallbackResponse("Unable to verify face. Please try again.", sharpness, brightness);
        } finally {
            if (image != null) {
                try { image.release(); } catch (Exception ignored) {}
            }
            if (buffer != null) {
                try { buffer.release(); } catch (Exception ignored) {}
            }
        }
    }

    private void ensureOpenCvLoaded() {
        if (openCvLoaded.compareAndSet(false, true)) {
            boolean loaded = OpenCVLoader.loadOrWarn();
            if (!loaded) {
                throw new IllegalStateException("OpenCV native library unavailable; face capture analysis disabled");
            }
        }
    }

    private byte[] decodeImage(String encoded) {
        String data = encoded.trim();
        int comma = data.indexOf(',');
        if (comma >= 0) {
            data = data.substring(comma + 1);
        }
        if (!StringUtils.hasText(data)) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to decode face capture frame: {}", ex.toString());
            throw new IllegalArgumentException("image_data is not valid base64", ex);
        }
    }

    private double estimateBrightness(Mat image) {
        Mat gray = new Mat();
        try {
            if (image.channels() == 1) {
                image.copyTo(gray);
            } else {
                Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            }
            Scalar meanScalar = org.opencv.core.Core.mean(gray);
            double value = meanScalar.val.length > 0 ? meanScalar.val[0] : 0.0d;
            return Math.min(1.0d, Math.max(0.0d, value / 255.0d));
        } finally {
            try { gray.release(); } catch (Exception ignored) {}
        }
    }

    private String resolveMessage(int faceCount, int boxedCount) {
        if (faceCount == 0) {
            return "No face detected";
        }
        if (faceCount > 1) {
            return "Multiple faces detected";
        }
        if (boxedCount != 1) {
            return "Unable to isolate face; adjust position and try again.";
        }
        return "Face detected";
    }

    private FaceCaptureAnalysisResponse fallbackResponse(String message) {
        return fallbackResponse(message, 0.0d, 0.0d);
    }

    private FaceCaptureAnalysisResponse fallbackResponse(String message, double sharpness, double brightness) {
        FaceCaptureAnalysisResponse response = new FaceCaptureAnalysisResponse();
        response.setValid(false);
        response.setFaceCount(0);
        response.setMessage(message);
        response.setSharpness(Math.max(0.0d, sharpness));
        response.setBrightness(Math.min(1.0d, Math.max(0.0d, brightness)));
        response.setBoundingBoxes(List.of());
        return response;
    }

    private FaceCaptureAnalysisResponse.BoundingBox toBoundingBox(Rectangle rect) {
        if (rect == null) {
            return null;
        }
        return new FaceCaptureAnalysisResponse.BoundingBox(rect.x, rect.y, rect.width, rect.height);
    }
}
