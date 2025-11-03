package com.smartattendance.util;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for loading OpenCV native libraries with graceful error handling.
 */
public final class OpenCVLoader {
    private static final Logger log = LoggerFactory.getLogger(OpenCVLoader.class);

    private OpenCVLoader() {}

    /**
     * Attempts to load OpenCV and logs any failure so the caller can decide how to proceed.
     *
     * @return true if loaded successfully; false otherwise
     */
    public static boolean loadOrWarn() {
        try {
            Loader.load(opencv_java.class);
            log.info("OpenCV native library loaded successfully.");
            return true;
        } catch (UnsatisfiedLinkError e) {
            log.error("OpenCV native library load failed: {}", e.toString());
            log.error("Vision features will be unavailable until the native libraries are installed.");
            return false;
        } catch (Exception t) {
            log.error("Unexpected error during OpenCV load", t);
            return false;
        }
    }
}






