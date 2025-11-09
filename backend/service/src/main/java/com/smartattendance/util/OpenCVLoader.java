package com.smartattendance.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.Core;
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
        if (loadViaNuPattern()) {
            return true;
        }
        if (loadViaSystemLibrary()) {
            return true;
        }
        return loadViaJavaCpp();
    }

    private static boolean loadViaNuPattern() {
        try {
            Class<?> openCvClass = Class.forName("nu.pattern.OpenCV");
            Method loadLocally = openCvClass.getMethod("loadLocally");
            loadLocally.invoke(null);
            log.info("OpenCV native library loaded via nu.pattern.OpenCV.");
            return true;
        } catch (ClassNotFoundException ex) {
            log.debug("nu.pattern.OpenCV not on classpath; skipping bundled loader");
            return false;
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            log.warn("nu.pattern.OpenCV.loadLocally() unavailable: {}", ex.toString());
            return false;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof UnsatisfiedLinkError || cause instanceof IllegalStateException) {
                log.warn("nu.pattern.OpenCV.loadLocally() failed: {}", cause.toString());
            } else {
                log.warn("Unexpected error while loading OpenCV with nu.pattern.OpenCV", cause != null ? cause : ex);
            }
            return false;
        } catch (UnsatisfiedLinkError ex) {
            log.warn("nu.pattern.OpenCV.loadLocally() failed: {}", ex.toString());
            return false;
        }
    }

    private static boolean loadViaSystemLibrary() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            log.info("OpenCV native library loaded via System.loadLibrary.");
            return true;
        } catch (UnsatisfiedLinkError ex) {
            log.warn("System.loadLibrary({}) failed: {}", Core.NATIVE_LIBRARY_NAME, ex.toString());
            return false;
        } catch (Exception ex) {
            log.warn("Unexpected error while loading OpenCV with System.loadLibrary", ex);
            return false;
        }
    }

    private static boolean loadViaJavaCpp() {
        try {
            Loader.load(opencv_java.class);
            log.info("OpenCV native library loaded via JavaCPP Loader.");
            return true;
        } catch (UnsatisfiedLinkError e) {
            log.error("OpenCV native library load failed via JavaCPP: {}", e.toString());
            log.error("Vision features will be unavailable until the native libraries are installed.");
            return false;
        } catch (Exception t) {
            log.error("Unexpected error during OpenCV load via JavaCPP", t);
            return false;
        }
    }
}






