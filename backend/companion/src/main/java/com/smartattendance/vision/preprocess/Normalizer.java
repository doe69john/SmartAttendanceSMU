package com.smartattendance.vision.preprocess;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Resizes images to a consistent size for recognition.
 */
public class Normalizer implements Preprocessor {
    private final Size size;

    public Normalizer(int width, int height) {
        this.size = new Size(width, height);
    }

    @Override
    public Mat process(Mat image) {
        Mat resized = new Mat();
        Imgproc.resize(image, resized, size);
        return resized;
    }
}
