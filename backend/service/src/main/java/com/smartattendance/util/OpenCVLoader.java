package com.smartattendance.util;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;

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
        if (loadViaJavaCpp()) {
            return true;
        }
        if (loadViaNuPattern()) {
            return true;
        }
        return loadViaSystemLibrary();
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
            logNativeDiagnostics();
            log.error("Vision features will be unavailable until the native libraries are installed.");
            return false;
        } catch (Exception t) {
            log.error("Unexpected error during OpenCV load via JavaCPP", t);
            return false;
        }
    }

    private static void logNativeDiagnostics() {
        try {
            log.warn("JavaCPP platform: {}", Loader.getPlatform());
        } catch (Exception platformEx) {
            log.debug("Unable to determine JavaCPP platform", platformEx);
        }

        try {
            File cacheDir = Loader.cacheDir();
            if (cacheDir != null) {
                log.warn("JavaCPP cache directory: {}", cacheDir.getAbsolutePath());
            }
        } catch (Exception cacheEx) {
            log.debug("Unable to query JavaCPP cache directory", cacheEx);
        }

        CodeSource codeSource = opencv_java.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL location = codeSource.getLocation();
            if (location != null) {
                log.warn("opencv_java.class is loaded from {}", location);
            }
        } else {
            log.debug("opencv_java.class code source unavailable");
        }

        String javaLibraryPath = System.getProperty("java.library.path");
        if (javaLibraryPath != null) {
            log.warn("java.library.path={}", javaLibraryPath);
        }

        logBytedecoClasspath();
    }

    private static void logBytedecoClasspath() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            List<String> bytedecoUrls = Arrays.stream(urlClassLoader.getURLs())
                    .map(URL::toString)
                    .filter(url -> url.contains("bytedeco"))
                    .toList();
            if (bytedecoUrls.isEmpty()) {
                log.warn("No org.bytedeco jars detected on the context class loader");
            } else {
                log.warn("Detected org.bytedeco artifacts on classpath: {}", bytedecoUrls);
            }
        } else if (classLoader != null) {
            log.debug("Context class loader does not expose URLs: {}", classLoader.getClass().getName());
        } else {
            log.debug("Context class loader is null");
        }
    }
}






