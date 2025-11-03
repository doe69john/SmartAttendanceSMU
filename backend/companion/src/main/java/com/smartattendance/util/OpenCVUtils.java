package com.smartattendance.util;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** Utility helpers for working with OpenCV Mats and BufferedImages. */
public final class OpenCVUtils {
    private OpenCVUtils() {}

    /** Converts a {@link BufferedImage} to an OpenCV {@link Mat}. */
    public static Mat bufferedImageToMat(BufferedImage bi) {
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);
        return mat;
    }

    /** Resizes a Mat to the given size. */
    public static Mat resize(Mat src, int width, int height) {
        Mat dst = new Mat();
        Imgproc.resize(src, dst, new Size(width, height));
        return dst;
    }
}
