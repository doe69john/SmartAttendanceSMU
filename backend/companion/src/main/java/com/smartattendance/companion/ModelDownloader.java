package com.smartattendance.companion;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelDownloader {

    private static final Logger logger = LoggerFactory.getLogger(ModelDownloader.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public ModelDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(TIMEOUT)
                .build();
    }

    public FileDownloadResult downloadTo(Path targetDirectory, String url, String fileName) {
        return downloadTo(targetDirectory, url, fileName, null);
    }

    public FileDownloadResult downloadTo(Path targetDirectory, String url, String fileName, String bearerToken) {
        ensureDirectory(targetDirectory);
        Path destination = targetDirectory.resolve(fileName);
        Path tempFile = destination.getParent().resolve(fileName + ".download");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/octet-stream");
            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken.trim());
            }
            logger.info("Companion attempting download of '{}' from {} (bearerProvided={})",
                    fileName, url, StringUtils.isNotBlank(bearerToken));
            HttpResponse<Path> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response.statusCode() >= 400) {
                logger.warn("Companion download of '{}' from {} failed: HTTP {}", fileName, url, response.statusCode());
                throw new CompanionHttpException(response.statusCode(),
                        "Failed to download model asset: HTTP " + response.statusCode());
            }
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(destination);
            String checksum = checksum(destination);
            logger.info("Downloaded companion asset {} ({} bytes, sha256={})", destination.getFileName(), size, checksum);
            return new FileDownloadResult(destination, size, checksum);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CompanionHttpException(499, "Download interrupted", ex);
        } catch (IOException ex) {
            logger.warn("Companion download of '{}' from {} encountered I/O error: {}", fileName, url, ex.getMessage());
            throw new CompanionHttpException(500, "Failed to download model asset: " + ex.getMessage(), ex);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new CompanionHttpException(500, "Unable to create directory " + directory, ex);
        }
    }

    private URI resolve(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException ex) {
            throw new CompanionHttpException(400, "Invalid asset URL: " + url, ex);
        }
    }

    public static String checksum(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException ex) {
            logger.warn("Unable to compute checksum for {}: {}", file, ex.getMessage());
            return "unknown";
        }
    }

    public record FileDownloadResult(Path path, long size, String checksum) {
    }
}
