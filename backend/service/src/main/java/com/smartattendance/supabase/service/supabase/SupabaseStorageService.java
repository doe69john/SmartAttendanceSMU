package com.smartattendance.supabase.service.supabase;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.smartattendance.supabase.auth.OutboundAuth;
import com.smartattendance.supabase.auth.SupabaseGoTrueProperties;
import com.smartattendance.supabase.config.SupabaseStorageProperties;
import com.smartattendance.supabase.dto.StorageDownload;
import com.smartattendance.supabase.dto.StorageObjectDto;
import com.smartattendance.supabase.dto.StorageUploadResult;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SupabaseStorageService {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final WebClient storageClient;
    private final SupabaseStorageProperties properties;
    private final String storageBaseUrl;
    private final boolean enabled;
    private final String disabledReason;
    private final String anonKey;
    private final ThreadLocal<String> bearerOverride = new ThreadLocal<>();
    private final ThreadLocal<String> apiKeyOverride = new ThreadLocal<>();

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    public SupabaseStorageService(WebClient.Builder webClientBuilder,
                                  SupabaseStorageProperties properties,
                                  SupabaseGoTrueProperties goTrueProperties,
                                  @Value("${supabase.project-url:}") String projectUrl) {
        this.properties = properties;
        this.anonKey = goTrueProperties != null && StringUtils.hasText(goTrueProperties.getAnonKey())
                ? goTrueProperties.getAnonKey().trim()
                : null;
        String normalizedBaseUrl = normalizeBaseUrl(projectUrl);

        WebClient client = null;
        boolean integrationEnabled = false;
        String disableReason = null;

        if (!StringUtils.hasText(anonKey)) {
            disableReason = "supabase.gotrue.anon-key is not configured";
        } else if (!StringUtils.hasText(normalizedBaseUrl)) {
            disableReason = "supabase.project-url is not configured";
        } else {
            client = webClientBuilder
                    .baseUrl(normalizedBaseUrl)
                    .filter(facesZipLoggingFilter())
                    .build();
            integrationEnabled = true;
            logger.info("Supabase storage integration enabled: bucket '{}' via {}",
                    properties.getFaceImageBucket(), normalizedBaseUrl);
        }

        if (!integrationEnabled) {
            logger.warn("Supabase storage integration disabled: {}", disableReason);
        }

        this.storageClient = client;
        this.storageBaseUrl = normalizedBaseUrl;
        this.enabled = integrationEnabled;
        this.disabledReason = disableReason;
    }

    private String normalizeBaseUrl(String projectUrl) {
        if (!StringUtils.hasText(projectUrl)) {
            return null;
        }
        String trimmed = projectUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/storage/v1";
    }

    private void ensureEnabled() {
        if (!enabled) {
            String reason = StringUtils.hasText(disabledReason) ? disabledReason : "Supabase storage is disabled";
            throw new IllegalStateException(reason);
        }
    }

    public StorageUploadResult upload(String bucket,
                                      String objectPath,
                                      MediaType mediaType,
                                      byte[] data,
                                      boolean upsert) {
        ensureEnabled();
        return storageClient.put()
                .uri(builder -> buildObjectUri(builder, bucket, objectPath))
                .headers(headers -> {
                    applyAuthHeaders(headers);
                    headers.set(HttpHeaders.CONTENT_TYPE,
                            mediaType != null ? mediaType.toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
                    if (upsert) {
                        headers.set("x-upsert", "true");
                    }
                })
                .bodyValue(data != null ? data : new byte[0])
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).flatMap(body -> {
                    logger.error("Supabase upload failed (bucket={}, objectPath={}, status={}): {}", bucket, objectPath, response.statusCode(), body);
                    return Mono.error(new IllegalStateException("Supabase upload failed with status " + response.statusCode()));
                }))
                .bodyToMono(StorageUploadResult.class)
                .block();
    }

    public StorageUploadResult uploadFile(String bucket,
                                          String objectPath,
                                          MediaType mediaType,
                                          Path file,
                                          boolean upsert) {
        ensureEnabled();
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }
        Flux<DataBuffer> data = DataBufferUtils.readInputStream(
                () -> Files.newInputStream(file),
                BUFFER_FACTORY,
                64 * 1024);
        return storageClient.put()
                .uri(builder -> buildObjectUri(builder, bucket, objectPath))
                .headers(headers -> {
                    applyAuthHeaders(headers);
                    headers.set(HttpHeaders.CONTENT_TYPE,
                            mediaType != null ? mediaType.toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
                    if (upsert) {
                        headers.set("x-upsert", "true");
                    }
                })
                .body(BodyInserters.fromDataBuffers(data))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).flatMap(body -> {
                    logger.error("Supabase upload failed (bucket={}, objectPath={}, status={}): {}", bucket, objectPath, response.statusCode(), body);
                    return Mono.error(new IllegalStateException("Supabase upload failed with status " + response.statusCode()));
                }))
                .bodyToMono(StorageUploadResult.class)
                .block();
    }

    public StorageDownload download(String bucket, String objectPath) {
        return download(bucket, objectPath, null);
    }

    public StorageDownload download(String bucket, String objectPath, Duration timeout) {
        ensureEnabled();
        boolean facesZipDownload = isFacesZipDownload(bucket, objectPath);
        Mono<StorageDownload> downloadMono = storageClient.get()
                .uri(builder -> buildObjectUri(builder, bucket, objectPath))
                .headers(this::applyAuthHeaders)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    MediaType mediaType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
                    // Log the actual content-type for faces.zip files
                    if (facesZipDownload) {
                        logger.info("Supabase faces.zip download content-type: {}", mediaType);
                    }
                    var optionalLength = response.headers().contentLength();
                    Long contentLength = optionalLength.isPresent() ? optionalLength.getAsLong() : null;
                    HttpHeaders headers = response.headers().asHttpHeaders();
                    if (facesZipDownload) {
                        HttpStatusCode statusCode = response.statusCode();
                        String eTag = headers.getFirst(HttpHeaders.ETAG);
                        String lastModified = headers.getFirst(HttpHeaders.LAST_MODIFIED);
                        logger.info(
                                "Supabase faces.zip download response: status={}, Content-Length={}, ETag={}, Last-Modified={}",
                                statusCode.value(),
                                contentLength != null ? contentLength : "(absent)",
                                StringUtils.hasText(eTag) ? eTag : "(absent)",
                                StringUtils.hasText(lastModified) ? lastModified : "(absent)");
                    }
                    Flux<DataBuffer> body = response.bodyToFlux(DataBuffer.class);
                    if (facesZipDownload) {
                        AtomicBoolean loggedFirstBytes = new AtomicBoolean(false);
                        AtomicBoolean hasData = new AtomicBoolean(false);
                        body = body.doOnNext(buffer -> {
                            hasData.set(true);
                            if (loggedFirstBytes.compareAndSet(false, true)) {
                                int readable = buffer.readableByteCount();
                                if (readable > 0) {
                                    int length = Math.min(readable, 8);
                                    byte[] preview = new byte[length];
                                    ByteBuffer view = buffer.asByteBuffer(buffer.readPosition(), length);
                                    view.get(preview);
                                    logger.info("Supabase faces.zip download first {} bytes (hex): {}",
                                            length, toHex(preview));
                                } else {
                                    logger.info("Supabase faces.zip download first bytes: <empty body>");
                                }
                            }
                        }).doOnComplete(() -> {
                            logger.info("Supabase faces.zip download body flux completed. Has data: {}", hasData.get());
                        }).doOnError(error -> {
                            logger.error("Supabase faces.zip download body flux error: {}", error.getMessage(), error);
                        }).doOnSubscribe(subscription -> {
                            logger.info("Supabase faces.zip download body flux subscribed");
                        });
                    }
                    return Mono.just(new StorageDownload(body, mediaType, contentLength, headers));
                });
        return timeout != null ? downloadMono.block(timeout) : downloadMono.block();
    }

    public byte[] downloadAsBytes(String bucket, String objectPath, Duration timeout) {
        ensureEnabled();
        boolean facesZipDownload = isFacesZipDownload(bucket, objectPath);
        boolean modelArchiveDownload = isModelArchiveDownload(bucket, objectPath);
        return storageClient.get()
                .uri(builder -> {
                    URI uri = buildObjectUri(builder, bucket, objectPath);
                    if (facesZipDownload) {
                        logger.info("Supabase faces.zip download payload: {} {}", HttpMethod.GET, uri);
                    }
                    if (modelArchiveDownload) {
                        logger.info("Supabase lbph.zip download payload: {} {}", HttpMethod.GET, uri);
                    }
                    return uri;
                })
                .headers(headers -> {
                    applyAuthHeaders(headers);
                    if (facesZipDownload) {
                        logger.info("Supabase faces.zip download headers: {}", sanitizeHeaders(headers));
                    }
                    if (modelArchiveDownload) {
                        logger.info("Supabase lbph.zip download headers: {}", sanitizeHeaders(headers));
                    }
                })
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .exchangeToMono(response -> {
                    if (facesZipDownload) {
                        logFacesZipResponse(response);
                    }
                    if (modelArchiveDownload) {
                        logModelDownloadResponse(response);
                    }
                    HttpHeaders responseHeaders = response.headers().asHttpHeaders();
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(error -> {
                            if (facesZipDownload) {
                                logger.warn(
                                        "Supabase faces.zip download failed: status={}, headers={}, body={}",
                                        error.getStatusCode().value(),
                                        sanitizeHeaders(responseHeaders),
                                        abbreviate(error.getResponseBodyAsString()));
                            }
                            if (modelArchiveDownload) {
                                logger.warn(
                                        "Supabase lbph.zip download failed: status={}, headers={}, body={}",
                                        error.getStatusCode().value(),
                                        sanitizeHeaders(responseHeaders),
                                        abbreviate(error.getResponseBodyAsString()));
                            }
                            return Mono.error(error);
                        });
                    }
                    // Stream and join all buffers
                    return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class))
                            .map(buffer -> {
                                try {
                                    byte[] bytes = new byte[buffer.readableByteCount()];
                                    buffer.read(bytes);
                                    return bytes;
                                } finally {
                                    DataBufferUtils.release(buffer);
                                }
                            });
                })
                .block(timeout);
    }

    public StorageObjectHead head(String bucket, String objectPath) {
        ensureEnabled();
        try {
            return storageClient.head()
                    .uri(builder -> buildObjectUri(builder, bucket, objectPath))
                    .headers(this::applyAuthHeaders)
                    .exchangeToMono(response -> {
                        if (response.statusCode().value() == 404) {
                            return Mono.just(new StorageObjectHead(true, false, null, null, null));
                        }
                        if (response.statusCode().isError()) {
                            return response.createException().flatMap(Mono::error);
                        }
                        HttpHeaders headers = response.headers().asHttpHeaders();
                        OffsetDateTime lastModified = parseLastModified(headers.getFirst(HttpHeaders.LAST_MODIFIED));
                        Long contentLength = headers.getContentLength() >= 0 ? headers.getContentLength() : null;
                        String eTag = headers.getETag();
                        return Mono.just(new StorageObjectHead(true, true, lastModified, contentLength, eTag));
                    })
                    .block();
        } catch (RuntimeException ex) {
            logger.debug("HEAD request failed for bucket '{}' object '{}': {}", bucket, objectPath, ex.getMessage());
            return new StorageObjectHead(false, false, null, null, null);
        }
    }

    public List<StorageObjectDto> list(String bucket,
                                       String prefix,
                                       Integer limit,
                                       Integer offset,
                                       String sortColumn,
                                       String sortOrder) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prefix", prefix != null ? prefix : "");
        payload.put("limit", limit != null ? limit : 100);
        payload.put("offset", offset != null ? offset : 0);
        payload.put("sortBy", Map.of(
                "column", StringUtils.hasText(sortColumn) ? sortColumn : "name",
                "order", normalizeOrder(sortOrder)));
        ensureEnabled();
        return storageClient.post()
                .uri(builder -> builder.pathSegment("object", "list", bucket).build())
                .headers(this::applyAuthHeaders)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(StorageObjectDto.class)
                .map(object -> {
                    if (!StringUtils.hasText(object.getBucketId())) {
                        object.setBucketId(bucket);
                    }
                    if (!StringUtils.hasText(object.getKey())) {
                        object.setKey(buildKey(bucket, prefix, object.getName()));
                    }
                    if (!StringUtils.hasText(object.getPath())) {
                        object.setPath(buildKey(null, prefix, object.getName()));
                    }
                    return object;
                })
                .collectList()
                .block();
    }

    public void delete(String bucket, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> sanitized = new ArrayList<>();
        for (String key : keys) {
            if (StringUtils.hasText(key)) {
                sanitized.add(normalizeKey(key));
            }
        }
        if (sanitized.isEmpty()) {
            return;
        }
        ensureEnabled();
        Map<String, Object> payload = Map.of("prefixes", sanitized);
        storageClient.method(HttpMethod.DELETE)
                .uri(builder -> builder.pathSegment("object", bucket).build())
                .headers(headers -> {
                    applyAuthHeaders(headers);
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                })
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public <T> T withBearer(String bearerToken, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        String normalized = OutboundAuth.ensureBearerFormat(bearerToken);
        bearerOverride.set(normalized);
        try {
            return action.get();
        } finally {
            bearerOverride.remove();
        }
    }

    public void withBearer(String bearerToken, Runnable action) {
        Objects.requireNonNull(action, "action");
        withBearer(bearerToken, () -> {
            action.run();
            return null;
        });
    }

    public <T> T withServiceKey(String serviceKey, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (!StringUtils.hasText(serviceKey)) {
            throw new IllegalArgumentException("Service key must not be blank");
        }
        String normalizedKey = serviceKey.trim();
        String bearer = OutboundAuth.ensureBearerFormat(normalizedKey);
        bearerOverride.set(bearer);
        apiKeyOverride.set(normalizedKey);
        try {
            return action.get();
        } finally {
            bearerOverride.remove();
            apiKeyOverride.remove();
        }
    }

    public void withServiceKey(String serviceKey, Runnable action) {
        Objects.requireNonNull(action, "action");
        withServiceKey(serviceKey, () -> {
            action.run();
            return null;
        });
    }

    private URI buildObjectUri(UriBuilder builder, String bucket, String objectPath) {
        UriBuilder working = builder.pathSegment("object", bucket);
        for (String segment : normalizeKey(objectPath).split("/")) {
            if (StringUtils.hasText(segment)) {
                working = working.pathSegment(segment);
            }
        }
        return working.build();
    }

    private String buildKey(String bucket, String prefix, String name) {
        StringBuilder key = new StringBuilder();
        if (StringUtils.hasText(bucket)) {
            key.append(bucket.trim());
            if (!bucket.endsWith("/")) {
                key.append('/');
            }
        }
        if (StringUtils.hasText(prefix)) {
            String normalized = prefix.startsWith("/") ? prefix.substring(1) : prefix;
            key.append(normalized);
            if (!normalized.endsWith("/") && StringUtils.hasText(name)) {
                key.append('/');
            }
        }
        if (StringUtils.hasText(name)) {
            key.append(name);
        }
        return key.toString();
    }

    private String normalizeOrder(String sortOrder) {
        if (!StringUtils.hasText(sortOrder)) {
            return "asc";
        }
        return switch (sortOrder.toLowerCase(Locale.ROOT)) {
            case "desc", "descending" -> "desc";
            default -> "asc";
        };
    }

    private void applyAuthHeaders(HttpHeaders headers) {
        Objects.requireNonNull(headers, "headers");
        String authorization = bearerOverride.get();
        if (!StringUtils.hasText(authorization)) {
            authorization = OutboundAuth.requireBearerToken();
        }
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        String apiKey = apiKeyOverride.get();
        if (!StringUtils.hasText(apiKey)) {
            if (!StringUtils.hasText(anonKey)) {
                throw new IllegalStateException("Supabase anon key is not configured");
            }
            apiKey = anonKey;
        }
        headers.set("apikey", apiKey);
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

    public SupabaseStorageProperties getProperties() {
        return properties;
    }

    public String getStorageBaseUrl() {
        return storageBaseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    private OffsetDateTime parseLastModified(String lastModifiedHeader) {
        if (!StringUtils.hasText(lastModifiedHeader)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(lastModifiedHeader, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException ex) {
            logger.debug("Unable to parse Last-Modified header '{}': {}", lastModifiedHeader, ex.getMessage());
            return null;
        }
    }

    private ExchangeFilterFunction facesZipLoggingFilter() {
        return (request, next) -> {
            if (shouldLogFacesZipRequest(request)) {
                logFacesZipRequest(request);
            }
            return next.exchange(request);
        };
    }

    private boolean shouldLogFacesZipRequest(ClientRequest request) {
        if (request == null || request.method() == null || request.url() == null) {
            return false;
        }
        if (!HttpMethod.GET.equals(request.method())) {
            return false;
        }
        if (!StringUtils.hasText(properties.getFaceZipBucket())) {
            return false;
        }
        String path = request.url().getPath();
        String bucketSegment = "/object/" + properties.getFaceZipBucket();
        return path != null && path.contains(bucketSegment) && path.endsWith("/faces.zip");
    }

    private void logFacesZipRequest(ClientRequest request) {
        try {
            logger.info("Supabase faces.zip download payload: {} {}", request.method(), request.url());
            logger.info("Supabase faces.zip download headers: {}", sanitizeHeaders(request.headers()));
        } catch (RuntimeException ex) {
            logger.debug("Failed to log faces.zip request payload: {}", ex.getMessage());
        }
    }

    private boolean isFacesZipDownload(String bucket, String objectPath) {
        if (!StringUtils.hasText(properties.getFaceZipBucket())) {
            return false;
        }
        if (!StringUtils.hasText(bucket) || !properties.getFaceZipBucket().equals(bucket)) {
            return false;
        }
        String normalizedPath = normalizeKey(objectPath);
        return StringUtils.hasText(normalizedPath) && normalizedPath.endsWith("/faces.zip");
    }

    private boolean isModelArchiveDownload(String bucket, String objectPath) {
        if (!StringUtils.hasText(properties.getFaceModelBucket())) {
            return false;
        }
        if (!StringUtils.hasText(bucket) || !properties.getFaceModelBucket().equals(bucket)) {
            return false;
        }
        String normalizedPath = normalizeKey(objectPath);
        return StringUtils.hasText(normalizedPath) && normalizedPath.endsWith("/lbph.zip");
    }

    private void logFacesZipResponse(org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        HttpStatusCode statusCode = response.statusCode();
        Long contentLength = headers.getContentLength() >= 0 ? headers.getContentLength() : null;
        logger.info("Supabase faces.zip download response: status={}, Content-Length={}, headers={}",
                statusCode.value(),
                contentLength != null ? contentLength : "(absent)",
                sanitizeHeaders(headers));
    }

    private void logModelDownloadResponse(org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        HttpStatusCode statusCode = response.statusCode();
        Long contentLength = headers.getContentLength() >= 0 ? headers.getContentLength() : null;
        String eTag = headers.getFirst(HttpHeaders.ETAG);
        String lastModified = headers.getFirst(HttpHeaders.LAST_MODIFIED);
        logger.info(
                "Supabase lbph.zip download response: status={}, Content-Length={}, ETag={}, Last-Modified={}, headers={}",
                statusCode.value(),
                contentLength != null ? contentLength : "(absent)",
                StringUtils.hasText(eTag) ? eTag : "(absent)",
                StringUtils.hasText(lastModified) ? lastModified : "(absent)",
                sanitizeHeaders(headers));
    }

    private String sanitizeHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) || "apikey".equalsIgnoreCase(name) ||
                    HttpHeaders.COOKIE.equalsIgnoreCase(name)) {
                sanitized.put(name, List.of("**redacted**"));
            } else {
                sanitized.put(name, values);
            }
        });
        return sanitized.toString();
    }

    private String abbreviate(String value) {
        if (!StringUtils.hasText(value)) {
            return "(empty)";
        }
        String trimmed = value.trim();
        int max = 512;
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }

    private String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 3 - 1);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format(Locale.ROOT, "%02X", bytes[i]));
        }
        return builder.toString();
    }

    public record StorageObjectHead(boolean accessible,
                                    boolean exists,
                                    OffsetDateTime lastModified,
                                    Long contentLength,
                                    String eTag) {
    }

}
