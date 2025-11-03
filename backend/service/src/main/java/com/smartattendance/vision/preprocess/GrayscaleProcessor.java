package com.smartattendance.vision.preprocess;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/**
 * Converts an image to grayscale.
 */
public class GrayscaleProcessor implements Preprocessor {
    @Override
    public Mat process(Mat image) {
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }
}
