package com.smartattendance.supabase.service.companion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartattendance.supabase.auth.OutboundAuth;
import com.smartattendance.supabase.config.SupabaseStorageProperties;
import com.smartattendance.supabase.dto.CompanionReleaseManifest;
import com.smartattendance.supabase.dto.CompanionReleaseManifest.CompanionReleaseInstaller;
import com.smartattendance.supabase.dto.CompanionReleaseResponse;
import com.smartattendance.supabase.dto.StorageDownload;
import com.smartattendance.supabase.service.supabase.SupabaseStorageService;

@Service
public class CompanionReleaseService {

    private static final Logger logger = LoggerFactory.getLogger(CompanionReleaseService.class);

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INSTALLER_DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);
    private static final String LATEST_MANIFEST_PATH = "releases/latest.json";

    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final String storageBaseUrl;

    public CompanionReleaseService(SupabaseStorageService storageService,
                                   SupabaseStorageProperties storageProperties,
                                   ObjectMapper objectMapper,
                                   @Value("${supabase.project-url:}") String supabaseProjectUrl) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
        this.storageBaseUrl = resolveStorageBaseUrl(storageService, supabaseProjectUrl);
    }

    public Optional<CompanionReleaseResponse> fetchLatestRelease() {
        String bucket = storageProperties.getCompanionInstallerBucket();
        if (!StringUtils.hasText(bucket)) {
            logger.warn("Companion installer bucket not configured. Set supabase.storage.companion-installer-bucket to enable releases endpoint.");
            return Optional.empty();
        }

        if (!StringUtils.hasText(storageBaseUrl)) {
            logger.warn("Supabase storage base URL is not configured. Unable to fetch companion release manifest.");
            return Optional.empty();
        }

        logger.info("Fetching companion release manifest '{}/{}'", bucket, LATEST_MANIFEST_PATH);
        Optional<CompanionReleaseManifest> manifestOptional = fetchLatestManifest(bucket);
        if (manifestOptional.isEmpty()) {
            return Optional.empty();
        }

        CompanionReleaseResponse response = mapManifest(manifestOptional.get());
        if (response == null) {
            logger.info("Companion release manifest '{}' did not map to a response", LATEST_MANIFEST_PATH);
            return Optional.empty();
        }

        logger.info(
                "Resolved companion release manifest '{}': version='{}', macPath='{}', windowsPath='{}'",
                LATEST_MANIFEST_PATH,
                response.getVersion(),
                response.getMacPath(),
                response.getWindowsPath());
        return Optional.of(response);
    }

    public Optional<InstallerDownload> downloadLatestInstaller(InstallerPlatform platform) {
        Optional<CompanionReleaseResponse> releaseOptional = fetchLatestRelease();
        if (releaseOptional.isEmpty()) {
            logger.warn("Unable to download companion installer: no release metadata available.");
            return Optional.empty();
        }

        CompanionReleaseResponse release = releaseOptional.get();
        String bucket = storageProperties.getCompanionInstallerBucket();
        if (!StringUtils.hasText(bucket)) {
            logger.warn("Unable to download companion installer: installer bucket not configured.");
            return Optional.empty();
        }

        String rawPath = switch (platform) {
            case MAC -> release.getMacPath();
            case WINDOWS -> release.getWindowsPath();
        };

        if (!StringUtils.hasText(rawPath)) {
            logger.warn("No installer path defined in manifest for platform '{}'.", platform.identifier());
            return Optional.empty();
        }

        String normalizedPath;
        try {
            normalizedPath = safeNormalize(rawPath);
        } catch (IllegalArgumentException ex) {
            logger.warn("Rejected companion installer path '{}' due to directory traversal attempt.", rawPath);
            return Optional.empty();
        }

        logger.info("Preparing to download companion installer for platform '{}' from path '{}'", platform.identifier(), normalizedPath);
        return downloadInstaller(bucket, normalizedPath, platform);
    }

    private Optional<CompanionReleaseManifest> fetchLatestManifest(String bucket) {
        if (storageService == null || !storageService.isEnabled()) {
            logger.warn("Supabase storage service disabled; unable to download companion manifest '{}'.", LATEST_MANIFEST_PATH);
            return Optional.empty();
        }
        try {
            return executeWithStorageBearer(() -> {
                logger.info("Downloading companion manifest '{}' from bucket '{}'", LATEST_MANIFEST_PATH, bucket);
                byte[] bytes = storageService.downloadAsBytes(bucket, LATEST_MANIFEST_PATH, DOWNLOAD_TIMEOUT);
                if (bytes == null || bytes.length == 0) {
                    logger.info("Companion release manifest '{}' not available (empty payload).", LATEST_MANIFEST_PATH);
                    return Optional.<CompanionReleaseManifest>empty();
                }
                logger.info("Companion release manifest '{}' downloaded ({} bytes)", LATEST_MANIFEST_PATH, bytes.length);
                try {
                    CompanionReleaseManifest manifest = objectMapper.readValue(bytes, CompanionReleaseManifest.class);
                    return Optional.of(manifest);
                } catch (IOException ex) {
                    logger.warn("Failed to parse companion release manifest '{}': {}", LATEST_MANIFEST_PATH, ex.getMessage(), ex);
                    return Optional.empty();
                }
            });
        } catch (WebClientResponseException.NotFound ex) {
            logger.info("Companion release manifest '{}' not found (404).", LATEST_MANIFEST_PATH);
            return Optional.empty();
        } catch (WebClientResponseException ex) {
            logger.info("Supabase storage returned {} for companion manifest '{}'.", ex.getStatusCode(), LATEST_MANIFEST_PATH);
            return Optional.empty();
        } catch (IllegalStateException ex) {
            logger.warn("Unable to download companion manifest '{}': {}", LATEST_MANIFEST_PATH, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            logger.warn("Failed to download companion manifest '{}': {}", LATEST_MANIFEST_PATH, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private Optional<InstallerDownload> downloadInstaller(String bucket, String objectPath, InstallerPlatform platform) {
        if (storageService == null || !storageService.isEnabled()) {
            logger.warn("Unable to download companion installer: Supabase storage integration is disabled.");
            return Optional.empty();
        }

        try {
            logger.info("Downloading companion installer '{}' from bucket '{}' path '{}'", platform.identifier(), bucket, objectPath);
            String downloadUrl = buildDownloadUrl(bucket, objectPath);
            if (StringUtils.hasText(downloadUrl)) {
                logger.info("Companion installer '{}' resolved download URL {}", platform.identifier(), downloadUrl);
            }
            byte[] bytes = executeWithStorageBearer(() ->
                    storageService.downloadAsBytes(bucket, objectPath, INSTALLER_DOWNLOAD_TIMEOUT));
            if (bytes == null || bytes.length == 0) {
                logger.warn("Companion installer '{}' not available: empty payload.", platform.identifier());
                return Optional.empty();
            }
            InputStream stream = new ByteArrayInputStream(bytes);
            String contentType = "application/zip";
            Long contentLength = (long) bytes.length;
            String filename = resolveFilename(null, objectPath, platform);

            long resolvedLength = contentLength != null ? contentLength : -1;
            if (resolvedLength < 0 && stream instanceof ByteArrayInputStream byteStream) {
                resolvedLength = byteStream.available();
            }

            logger.info("Companion installer '{}' download completed: filename='{}', contentType='{}', contentLength={} bytes", platform.identifier(), filename, contentType, resolvedLength);
            return Optional.of(new InstallerDownload(stream, contentType, resolvedLength, filename));
        } catch (WebClientResponseException.NotFound ex) {
            logger.warn("Companion installer '{}' not found at path '{}'.", platform.identifier(), objectPath);
            return Optional.empty();
        } catch (WebClientResponseException ex) {
            logger.warn("Failed to download companion installer '{}' (HTTP {}).", platform.identifier(), ex.getStatusCode());
            return Optional.empty();
        } catch (IllegalStateException ex) {
            logger.warn("Unable to download companion installer '{}': {}", platform.identifier(), ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            logger.warn("Exception while downloading companion installer '{}': {}", platform.identifier(), ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private String resolveFilename(StorageDownload download, String objectPath, InstallerPlatform platform) {
        HttpHeaders headers = download != null ? download.getHeaders() : null;
        if (headers != null) {
            String disposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (StringUtils.hasText(disposition)) {
                String resolved = sanitizeFilename(extractFilenameFromDisposition(disposition));
                if (StringUtils.hasText(resolved)) {
                    return resolved;
                }
            }
            String fallback = headers.getFirst("content-disposition");
            if (StringUtils.hasText(fallback)) {
                String resolved = sanitizeFilename(extractFilenameFromDisposition(fallback));
                if (StringUtils.hasText(resolved)) {
                    return resolved;
                }
            }
        }

        if (StringUtils.hasText(objectPath)) {
            int index = objectPath.lastIndexOf('/') + 1;
            if (index > 0 && index < objectPath.length()) {
                return sanitizeFilename(objectPath.substring(index));
            }
        }

        return platform.defaultFilename();
    }

    private String extractFilenameFromDisposition(String disposition) {
        if (!StringUtils.hasText(disposition)) {
            return null;
        }

        String[] segments = disposition.split(";");
        for (String segment : segments) {
            String trimmed = segment.trim();
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("filename*=")) {
                int equalsIdx = trimmed.indexOf('=');
                if (equalsIdx > -1) {
                    String value = trimmed.substring(equalsIdx + 1).trim();
                    value = value.replaceAll("^\"|\"$", "");
                    value = value.replaceFirst("(?i)^utf-8''", "");
                    try {
                        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                        if (StringUtils.hasText(decoded)) {
                            return decoded;
                        }
                    } catch (IllegalArgumentException ignored) {
                        if (StringUtils.hasText(value)) {
                            return value;
                        }
                    }
                }
            } else if (lower.startsWith("filename=")) {
                int equalsIdx = trimmed.indexOf('=');
                if (equalsIdx > -1 && equalsIdx + 1 < trimmed.length()) {
                    String value = trimmed.substring(equalsIdx + 1).trim();
                    value = value.replaceAll("^\"|\"$", "");
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String buildDownloadUrl(String bucket, String objectPath) {
        if (!StringUtils.hasText(storageBaseUrl) || !StringUtils.hasText(bucket) || !StringUtils.hasText(objectPath)) {
            return null;
        }

        String normalizedBase = storageBaseUrl.endsWith("/")
                ? storageBaseUrl.substring(0, storageBaseUrl.length() - 1)
                : storageBaseUrl;
        String normalizedBucket = bucket.startsWith("/") ? bucket.substring(1) : bucket;
        String normalizedPath = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;
        return String.format("%s/object/%s/%s?download", normalizedBase, normalizedBucket, normalizedPath);
    }

    private CompanionReleaseResponse mapManifest(CompanionReleaseManifest manifest) {
        if (manifest == null) {
            return null;
        }

        Map<String, CompanionReleaseInstaller> installers = manifest.getInstallers();
        CompanionReleaseInstaller macInstaller = !CollectionUtils.isEmpty(installers)
                ? installers.getOrDefault("mac", installers.get("macos"))
                : null;
        CompanionReleaseInstaller windowsInstaller = !CollectionUtils.isEmpty(installers)
                ? installers.getOrDefault("windows", installers.get("win"))
                : null;

        String macPath = resolveInstallerPath(macInstaller);
        String windowsPath = resolveInstallerPath(windowsInstaller);

        String macUrl = macInstaller != null ? trimToNull(macInstaller.getUrl()) : null;
        String windowsUrl = windowsInstaller != null ? trimToNull(windowsInstaller.getUrl()) : null;

        CompanionReleaseResponse response = new CompanionReleaseResponse();
        response.setVersion(trimToNull(manifest.getVersion()));
        response.setNotes(trimToNull(manifest.getNotes()));
        response.setPublishedAt(trimToNull(manifest.getPublishedAt()));
        response.setMacUrl(macUrl);
        response.setWindowsUrl(windowsUrl);
        response.setMacPath(macPath);
        response.setWindowsPath(windowsPath);
        response.setBucket(storageProperties.getCompanionInstallerBucket());
        response.setPublicBaseUrl(resolvePublicBaseUrl());
        return response;
    }

    private String resolveInstallerPath(CompanionReleaseInstaller installer) {
        if (installer == null || !StringUtils.hasText(installer.getPath())) {
            return null;
        }
        return normalizeKey(installer.getPath());
    }


    private String safeNormalize(String raw) {
        String sanitized = normalizeKey(raw);
        try {
            sanitized = URLDecoder.decode(sanitized, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // best effort decoding; fall back to sanitized value
        }
        if (!StringUtils.hasText(sanitized)) {
            return sanitized;
        }
        try {
            String normalized = Paths.get(sanitized).normalize().toString().replace("\\", "/");
            if (normalized.startsWith("../") || normalized.contains("/../")) {
                throw new IllegalArgumentException("Invalid path");
            }
            return normalized;
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid path", ex);
        }
    }

    private String resolvePublicBaseUrl() {
        String configured = storageProperties.getPublicBaseUrl();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        if (!StringUtils.hasText(storageBaseUrl)) {
            return null;
        }
        String normalized = storageBaseUrl.endsWith("/")
                ? storageBaseUrl.substring(0, storageBaseUrl.length() - 1)
                : storageBaseUrl;
        return normalized + "/object/public";
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        String normalized = key.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String captureStorageBearer() {
        return OutboundAuth.resolveBearerToken()
                .orElseGet(() -> {
                    String fallback = storageProperties != null ? storageProperties.getServiceAccessToken() : null;
                    if (!StringUtils.hasText(fallback)) {
                        throw new IllegalStateException(
                                "No Supabase JWT available for storage operations; configure supabase.storage.service-access-token");
                    }
                    return OutboundAuth.ensureBearerFormat(fallback);
                });
    }

    private <T> T executeWithStorageBearer(Supplier<T> action) {
        return storageService.withBearer(captureStorageBearer(), action);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveStorageBaseUrl(SupabaseStorageService storageService, String projectUrl) {
        if (storageService != null && StringUtils.hasText(storageService.getStorageBaseUrl())) {
            return storageService.getStorageBaseUrl();
        }
        if (storageProperties == null) {
            return null;
        }
        if (!StringUtils.hasText(projectUrl)) {
            return null;
        }
        String trimmed = projectUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/storage/v1";
    }

    public enum InstallerPlatform {
        MAC("mac", "smartattendance-companion-mac.zip"),
        WINDOWS("windows", "smartattendance-companion-windows.zip");

        private final String identifier;
        private final String defaultFilename;

        InstallerPlatform(String identifier, String defaultFilename) {
            this.identifier = identifier;
            this.defaultFilename = defaultFilename;
        }

        public String identifier() {
            return identifier;
        }

        public String defaultFilename() {
            return defaultFilename;
        }

        public static Optional<InstallerPlatform> from(String value) {
            if (!StringUtils.hasText(value)) {
                return Optional.empty();
            }
            String normalized = value.trim().toLowerCase();
            for (InstallerPlatform platform : values()) {
                if (platform.identifier.equals(normalized)) {
                    return Optional.of(platform);
                }
            }
            return Optional.empty();
        }
    }

    public static final class InstallerDownload implements AutoCloseable {
        private final InputStream inputStream;
        private final String contentType;
        private final long contentLength;
        private final String filename;

        public InstallerDownload(InputStream inputStream, String contentType, long contentLength, String filename) {
            this.inputStream = inputStream;
            this.contentType = contentType != null ? contentType : "application/octet-stream";
            this.contentLength = contentLength;
            this.filename = StringUtils.hasText(filename) ? filename : "companion-installer.zip";
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public String getContentType() {
            return contentType;
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getFilename() {
            return filename;
        }

        public void transferTo(OutputStream outputStream) throws IOException {
            inputStream.transferTo(outputStream);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }

    private String sanitizeFilename(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            return trimmed;
        }
        String decoded = decodeMimeEncodedWord(trimmed);
        String candidate = StringUtils.hasText(decoded) ? decoded : trimmed;
        return candidate.replace("\r", "").replace("\n", "");
    }

    private String decodeMimeEncodedWord(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();

        if (trimmed.startsWith("=?") && trimmed.endsWith("?=")) {
            return decodeRfc2047Word(trimmed);
        }

        if (trimmed.toUpperCase().startsWith("=_UTF-8_Q_") && trimmed.endsWith("_=")) {
            String inner = trimmed.substring("=_UTF-8_Q_".length(), trimmed.length() - 2);
            return decodeQuotedPrintableWord(inner, StandardCharsets.UTF_8);
        }

        return trimmed;
    }

    private String decodeRfc2047Word(String value) {
        Matcher matcher = RFC_2047_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        String charsetName = matcher.group(1);
        String encoding = matcher.group(2);
        String encodedText = matcher.group(3);

        Charset charset;
        try {
            charset = Charset.forName(charsetName);
        } catch (Exception ex) {
            charset = StandardCharsets.UTF_8;
        }

        if ("Q".equalsIgnoreCase(encoding)) {
            return decodeQuotedPrintableWord(encodedText, charset);
        }
        if ("B".equalsIgnoreCase(encoding)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedText);
                return new String(decoded, charset);
            } catch (IllegalArgumentException ex) {
                return value;
            }
        }
        return value;
    }

    private String decodeQuotedPrintableWord(String encoded, Charset charset) {
        if (encoded == null) {
            return null;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '_') {
                buffer.write(' ');
            } else if (c == '=' && i + 2 < encoded.length()) {
                String hex = encoded.substring(i + 1, i + 3);
                try {
                    buffer.write(Integer.parseInt(hex, 16));
                    i += 2;
                } catch (NumberFormatException ex) {
                    buffer.write(c);
                }
            } else {
                buffer.write((byte) c);
            }
        }
        return new String(buffer.toByteArray(), charset);
    }

    private static final Pattern RFC_2047_PATTERN =
            Pattern.compile("^=\\?([^?]+)\\?([bBqQ])\\?([^?]+)\\?=$");
}
