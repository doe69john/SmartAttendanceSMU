package com.smartattendance.vision.preprocess;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

/** Utility helpers for computing basic image quality metrics. */
public final class ImageQuality {
    private ImageQuality() {}

    /**
     * Computes the Laplacian variance for the provided image. Higher values
     * indicate a sharper image with more texture.
     */
    public static double laplacianVariance(Mat image) {
        if (image == null || image.empty()) {
            return 0.0d;
        }
        Mat gray = image;
        boolean created = false;
        if (image.channels() != 1) {
            gray = new Mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            created = true;
        }
        Mat lap = new Mat();
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        try {
            Imgproc.Laplacian(gray, lap, CvType.CV_64F);
            org.opencv.core.Core.meanStdDev(lap, mean, std);
            double[] stdArray = std.toArray();
            double sigma = stdArray.length > 0 ? stdArray[0] : 0.0d;
            return sigma * sigma;
        } finally {
            try { lap.release(); } catch (Exception ignored) {}
            try { mean.release(); } catch (Exception ignored) {}
            try { std.release(); } catch (Exception ignored) {}
            if (created) {
                try { gray.release(); } catch (Exception ignored) {}
            }
        }
    }

    /** Returns true if the Laplacian variance meets or exceeds the threshold. */
    public static boolean isSharpEnough(Mat image, double threshold) {
        if (threshold <= 0.0d) {
            return true;
        }
        double variance = laplacianVariance(image);
        return Double.isFinite(variance) && variance >= threshold;
    }
}
