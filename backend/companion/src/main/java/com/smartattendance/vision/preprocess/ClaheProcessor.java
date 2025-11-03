package com.smartattendance.vision.preprocess;

import org.opencv.core.Mat;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

/**
 * Applies CLAHE (adaptive histogram equalization) to improve contrast on
 * grayscale images while limiting amplification of noise. If the input has
 * more than one channel, it is converted to grayscale first.
 */
public class ClaheProcessor implements Preprocessor {
    @Override
    public Mat process(Mat image) {
        Mat gray = new Mat();
        if (image.channels() == 1) {
            image.copyTo(gray);
        } else {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        }
        Mat eq = new Mat();
        CLAHE clahe = Imgproc.createCLAHE();
        clahe.apply(gray, eq);
        gray.release();
        return eq;
    }
}

