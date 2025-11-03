package com.smartattendance.vision;

import java.io.IOException;
import java.nio.file.Path;

import org.opencv.core.Mat;

/**
 * Generic interface for face recognizers.
 * Implementations may apply preprocessing before running prediction.
 */
public interface Recognizer {
    /**
     * Trains the recognizer on images rooted at the given directory.
     */
    void train(Path root) throws IOException;

    /**
     * Recognizes the given face image and returns a prediction.
     */
    Prediction recognize(Mat face);

    /** Returns true if this recognizer can be updated incrementally. */
    default boolean supportsIncremental() { return false; }

    /**
     * Incrementally updates the recognizer with images from the given student's directory.
     * Implementations that do not support this should throw UnsupportedOperationException.
     */
    default void updateIncremental(Path studentDir, String studentId) throws IOException {
        throw new UnsupportedOperationException("Incremental update not supported");
    }

    /**
     * Incrementally removes the given student's data from the recognizer.
     * Implementations that do not support this should throw
     * UnsupportedOperationException.
     */
    default void removeStudent(String studentId) throws IOException {
        throw new UnsupportedOperationException("Incremental removal not supported");
    }

    /** Persist the model (and label mapping if applicable). Optional. */
    default void saveModel(Path modelDir) throws IOException {}

    /** Load the model (and label mapping if applicable). Optional. */
    default void loadModel(Path modelDir) throws IOException {}

    /** Simple prediction result. */
    record Prediction(String id, double confidence) {}
}
