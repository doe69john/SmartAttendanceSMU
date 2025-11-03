package com.smartattendance.vision.preprocess;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.smartattendance.config.AttendanceProperties;

/** Utility to generate simple data augmentation variants. */
public final class Augmenter {
    private Augmenter() {}

    /**
     * Returns a list of augmented variants including the original. Uses sensible defaults
     * if config is null.
     */
    public static List<Mat> augment(Mat src, AttendanceProperties config) {
        List<Mat> out = new ArrayList<>();
        double blurThreshold = 0.0d;
        if (config != null && config.capture() != null) {
            blurThreshold = Math.max(blurThreshold, config.capture().postCaptureBlurVarianceThreshold());
        }
        boolean flip = true;
        double[] degrees = new double[] { -10.0, 10.0 };
        if (config != null) {
            // Hook for future tunables (rotate, flip, etc.)
        }
        Mat base = src.clone();
        if (ImageQuality.isSharpEnough(base, blurThreshold)) {
            out.add(base);
        } else {
            base.release();
        }
        // Horizontal flip
        if (flip) {
            Mat f = new Mat();
            try {
                Core.flip(src, f, 1);
                if (ImageQuality.isSharpEnough(f, blurThreshold)) {
                    out.add(f);
                } else {
                    f.release();
                }
            } catch (Exception t) {
                if (f != null) try { f.release(); } catch (Exception ignored) {}
            }
        }
        // Small rotations around center
        for (double deg : degrees) {
            Mat rot = rotateKeepSize(src, deg);
            if (rot != null && !rot.empty()) {
                if (ImageQuality.isSharpEnough(rot, blurThreshold)) {
                    out.add(rot);
                } else {
                    rot.release();
                }
            }
        }
        return out;
    }

    private static Mat rotateKeepSize(Mat src, double degrees) {
        try {
            int w = src.cols(); int h = src.rows();
            Point center = new Point(w / 2.0, h / 2.0);
            Mat M = Imgproc.getRotationMatrix2D(center, degrees, 1.0);
            Mat dst = new Mat();
            Imgproc.warpAffine(src, dst, M, new Size(w, h), Imgproc.INTER_LINEAR, Core.BORDER_REFLECT);
            M.release();
            return dst;
        } catch (Exception t) {
            return null;
        }
    }
}
