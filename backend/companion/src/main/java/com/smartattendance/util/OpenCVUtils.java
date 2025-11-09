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

    /** Converts an OpenCV {@link Mat} to a {@link BufferedImage}. */
    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }
        int channels = mat.channels();
        int type = channels > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        Mat converted = mat;
        if (mat.type() != CvType.CV_8UC1 && mat.type() != CvType.CV_8UC3) {
            converted = new Mat();
            mat.convertTo(converted, channels > 1 ? CvType.CV_8UC3 : CvType.CV_8UC1);
        }
        int size = converted.channels() * converted.cols() * converted.rows();
        byte[] data = new byte[size];
        converted.get(0, 0, data);
        BufferedImage image = new BufferedImage(converted.cols(), converted.rows(), type);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(data, 0, target, 0, Math.min(data.length, target.length));
        if (converted != mat) {
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
