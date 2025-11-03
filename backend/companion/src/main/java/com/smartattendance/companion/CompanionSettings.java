package com.smartattendance.companion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record CompanionSettings(String host,
                                int port,
                                Path storageDir,
                                String version,
                                String backendBaseUrl,
                                String serviceToken) {

    private static final Logger logger = LoggerFactory.getLogger(CompanionSettings.class);
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 4455;
    private static final String VERSION_RESOURCE = "/companion-version.properties";
    private static final String ENV_PREFIX = "SMARTATTENDANCE_COMPANION_";

    public static CompanionSettings load() {
        String host = readEnv("HOST", DEFAULT_HOST);
        int port = parsePort(readEnv("PORT", String.valueOf(DEFAULT_PORT)));
        Path storageDir = resolveStorageDir(readEnv("DATA_DIR", null));
        String backendBaseUrl = sanitizeBackendUrl(readEnv("BACKEND_URL", "http://localhost:18080/api"));
        String serviceToken = readEnv("SERVICE_TOKEN", "");
        ensureDirectory(storageDir);
        String version = readVersion();
        return new CompanionSettings(host, port, storageDir, version, backendBaseUrl, serviceToken);
    }

    private static String readEnv(String key, String fallback) {
        String value = System.getenv(ENV_PREFIX + key);
        if (value == null) {
            value = System.getProperty("companion." + key.toLowerCase(Locale.ROOT));
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String sanitizeBackendUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "http://localhost:18080/api";
        }
        String normalized = raw.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static int parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || parsed > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return parsed;
        } catch (Exception ex) {
            logger.warn("Invalid port '{}', defaulting to {}", value, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static Path resolveStorageDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath();
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return Paths.get("companion-data").toAbsolutePath();
        }
        return Paths.get(userHome, ".smartattendance", "companion").toAbsolutePath();
    }

    private static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create companion data directory at " + path, ex);
        }
    }

    private static String readVersion() {
        try (InputStream inputStream = CompanionSettings.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (inputStream == null) {
                logger.warn("Missing version descriptor '{}'. Falling back to development version.", VERSION_RESOURCE);
                return "dev";
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version");
            return version != null && !version.isBlank() ? version.trim() : "dev";
        } catch (IOException ex) {
            logger.warn("Unable to read companion version descriptor: {}", ex.getMessage());
            return "dev";
        }
    }
}
