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

    /** Converts an OpenCV {@link Mat} into a {@link BufferedImage}. */
    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }
        Mat source = mat;
        Mat converted = null;
        if (mat.channels() == 1) {
            converted = new Mat();
            Imgproc.cvtColor(mat, converted, Imgproc.COLOR_GRAY2BGR);
            source = converted;
        }
        int width = source.cols();
        int height = source.rows();
        int channels = source.channels();
        int bufferSize = width * height * channels;
        byte[] sourcePixels = new byte[bufferSize];
        source.get(0, 0, sourcePixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, Math.min(sourcePixels.length, targetPixels.length));
        if (converted != null) {
            converted.release();
        }
        return image;
    }

    /** Resizes a Mat to the given size. */
    public static Mat resize(Mat src, int width, int height) {
        Mat dst = new Mat();
        Imgproc.resize(src, dst, new Size(width, height));
        return dst;
    }
}
