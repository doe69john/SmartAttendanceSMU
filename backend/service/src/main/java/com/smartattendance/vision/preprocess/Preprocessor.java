package com.smartattendance.vision.preprocess;

import org.opencv.core.Mat;

/**
 * Strategy interface for preprocessing operations applied before recognition.
 */
@FunctionalInterface
public interface Preprocessor {
    /** Applies this preprocessing step to the provided image. */
    Mat process(Mat image);
}
