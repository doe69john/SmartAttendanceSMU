package com.smartattendance.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.smartattendance.vision.HaarFaceDetector;
import com.smartattendance.vision.ModelManager;

/**
 * Prepares vision assets on startup and ensures the model manager is configured
 * with the runtime directories supplied by {@link AttendanceProperties}.
 */
@Configuration
public class VisionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VisionConfiguration.class);

    private final AttendanceProperties properties;
    private final ResourceLoader resourceLoader;

    public VisionConfiguration(AttendanceProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    ApplicationRunner visionAssetsInitializer(ModelManager modelManager) {
        return args -> {
            var dirs = properties.directories();
            List<String> cascades = List.of(
                    "haarcascade_frontalface_default.xml",
                    "haarcascade_eye_tree_eyeglasses.xml",
                    "haarcascade_frontalface_alt_tree.xml");
            for (String cascade : cascades) {
                copyAsset("classpath:vision/models/" + cascade, dirs.modelDir());
            }
            modelManager.configure(dirs.facesDir(), dirs.modelDir());
        };
    }

    private void copyAsset(String resourcePath, Path targetDir) {
        try {
            Resource resource = resourceLoader.getResource(resourcePath);
            if (!resource.exists()) {
                log.warn("Vision asset not found on classpath: {}", resourcePath);
                return;
            }
            Path target = targetDir.resolve(resource.getFilename());
            Files.createDirectories(targetDir);
            if (Files.exists(target)) {
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, target);
                log.info("Provisioned vision asset {}", target);
            }
        } catch (IOException ex) {
            log.warn("Failed to provision vision asset {}: {}", resourcePath, ex.getMessage());
        }
    }

    @Bean
    public HaarFaceDetector haarFaceDetector() {
        Path cascade = properties.directories().modelDir().resolve("haarcascade_frontalface_alt_tree.xml");
        return new HaarFaceDetector(cascade.toString(), properties);
    }
}
