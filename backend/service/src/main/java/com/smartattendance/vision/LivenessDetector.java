package com.smartattendance.vision;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfRect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/**
 * Liveness detector using a simple blink cycle (eyes visible -> not visible ->
 * visible within a short window) plus a texture check via Laplacian variance.
 */
public class LivenessDetector {
    private final CascadeClassifier eyeCascade;
    private boolean eyesPreviouslyVisible = true;
    private Long blinkStartMs = null;
    private static final long BLINK_MIN_MS = 50;   // too short = noise
    private static final long BLINK_MAX_MS = 1500; // allow longer natural blinks
    private final double textureThreshold;

    public LivenessDetector(String eyeCascadePath) {
        this(eyeCascadePath, 3.0);
    }

    public LivenessDetector(String eyeCascadePath, double textureThreshold) {
        this.eyeCascade = new CascadeClassifier(eyeCascadePath);
        this.textureThreshold = Math.max(0.0, textureThreshold);
    }

    /** Returns true iff a blink cycle just completed and texture is sufficient. */
    public synchronized boolean updateAndIsLive(Mat face) {
        Mat gray = new Mat();
        Imgproc.cvtColor(face, gray, Imgproc.COLOR_BGR2GRAY);
        CLAHE clahe = Imgproc.createCLAHE();
        clahe.apply(gray, gray);

        boolean eyesVisible = isEyesVisible(gray);
        long now = System.currentTimeMillis();
        boolean justBlinked = false;
        if (eyesPreviouslyVisible && !eyesVisible) {
            blinkStartMs = now;
        } else if (!eyesPreviouslyVisible && eyesVisible && blinkStartMs != null) {
            long dur = now - blinkStartMs;
            justBlinked = dur >= BLINK_MIN_MS && dur <= BLINK_MAX_MS;
            blinkStartMs = null;
        }
        eyesPreviouslyVisible = eyesVisible;

        double variance = laplacianVariance(gray);
        gray.release();
        return justBlinked && variance > textureThreshold;
    }

    /** Backward-compatible alias used by existing code paths. */
    public boolean isLive(Mat face) {
        return updateAndIsLive(face);
    }

    /** True if two eyes are detected in the given face ROI. */
    public synchronized boolean isEyesVisible(Mat faceOrGray) {
        Mat gray = faceOrGray;
        boolean created = false;
        if (faceOrGray.channels() != 1) {
            gray = new Mat();
            Imgproc.cvtColor(faceOrGray, gray, Imgproc.COLOR_BGR2GRAY);
            CLAHE claheInner = Imgproc.createCLAHE();
            claheInner.apply(gray, gray);
            created = true;
        }
        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(gray, eyes, 1.1, 2, 0, new Size(24, 24), new Size());
        boolean visible = eyes.toArray().length >= 1; // accept one eye visible
        if (created) gray.release();
        return visible;
    }

    private static double laplacianVariance(Mat gray) {
        Mat lap = new Mat();
        Imgproc.Laplacian(gray, lap, CvType.CV_64F);
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        org.opencv.core.Core.meanStdDev(lap, mean, std);
        double variance = std.toArray()[0] * std.toArray()[0];
        lap.release();
        return variance;
    }
}
