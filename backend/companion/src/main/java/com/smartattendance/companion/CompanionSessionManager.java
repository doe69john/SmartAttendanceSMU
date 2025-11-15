package com.smartattendance.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.smartattendance.companion.recognition.LiveRecognitionRuntime;
import com.smartattendance.companion.recognition.RecognitionEventBus;
import com.smartattendance.config.AttendanceProperties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompanionSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(CompanionSessionManager.class);
    private static final String TOKEN_KEY = "token";
    private static final String ISSUED_AT_KEY = "issuedAt";

    private final CompanionSettings settings;
    private final ObjectMapper objectMapper;
    private final ModelDownloader downloader;
    private final Path handshakeFile;
    private final Path sessionsDirectory;
    private final AttendanceProperties attendanceProperties;
    private final AtomicReference<SessionRuntime> activeSession = new AtomicReference<>();
    private volatile String handshakeToken;

    public CompanionSessionManager(CompanionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.downloader = new ModelDownloader();
        this.handshakeFile = settings.storageDir().resolve("handshake.json");
        this.sessionsDirectory = settings.storageDir().resolve("sessions");
        this.attendanceProperties = new AttendanceProperties();
        loadHandshakeToken();
    }

    public synchronized HandshakeResponse performHandshake(HandshakeRequest request) {
        String token = UUID.randomUUID().toString();
        this.handshakeToken = token;
        persistHandshake(token);
        SessionRuntime runtime = activeSession.get();
        String message = runtime != null && runtime.state().isActive()
                ? "Companion connected. Session " + runtime.state().sessionId() + " is active."
                : "Companion ready.";
        return new HandshakeResponse("healthy", token, settings.version(), message);
    }

    public HealthResponse health() {
        SessionRuntime runtime = activeSession.get();
        boolean sessionActive = runtime != null && runtime.state().isActive();
        String message = sessionActive ? "Session " + runtime.state().sessionId() + " running" : "Idle";
        return new HealthResponse("ok", settings.version(), sessionActive, message);
    }

    public SessionRuntime activeSession() {
        return activeSession.get();
    }

    public synchronized StartSessionResponse startSession(String token, StartSessionRequest request) {
        verifyToken(token);
        validateStartRequest(request);

        try {
            Files.createDirectories(sessionsDirectory);
        } catch (IOException ex) {
            throw new CompanionHttpException(500, "Unable to create sessions directory", ex);
        }

        Path sessionDir = sessionsDirectory.resolve(request.sessionId());
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException ex) {
            throw new CompanionHttpException(500, "Unable to create session workspace", ex);
        }

        String bearerToken = normalizeAccessToken(request.authToken());
        String maskedToken = maskToken(bearerToken);
        String backendBaseUrl = selectBackendBaseUrl(request.backendBaseUrl());

        String labelsUrl = resolveLabelsUrl(request);
        logger.info(
                "Starting asset downloads for companion session {} (section={}). modelUrl={}, labelsUrl={}, cascadeUrl={}, backendBaseUrl={}, bearerPresent={}, token={}",
                request.sessionId(),
                request.sectionId(),
                request.modelUrl(),
                labelsUrl,
                request.cascadeUrl(),
                backendBaseUrl,
                StringUtils.isNotBlank(bearerToken),
                maskedToken);

        ModelDownloader.FileDownloadResult model = downloader.downloadTo(sessionDir, request.modelUrl(), "lbph.yml", bearerToken);
        ModelDownloader.FileDownloadResult labels = downloader.downloadTo(sessionDir, labelsUrl, "labels.txt", bearerToken);
        ModelDownloader.FileDownloadResult cascade = downloader.downloadTo(sessionDir, request.cascadeUrl(), "haarcascade_frontalface_default.xml", bearerToken);

        SessionState state = new SessionState(
                request.sessionId(),
                request.sectionId(),
                sessionDir,
                request.missingStudentIds(),
                request.labels(),
                bearerToken,
                backendBaseUrl,
                request.scheduledStart(),
                request.scheduledEnd(),
                request.lateThresholdMinutes());
        long totalBytes = model.size() + cascade.size() + labels.size();
        state.registerAssets(model.path(), cascade.path(), labels.path(), totalBytes);
        writeSessionMetadata(state, model, cascade, labels);
        cacheCascadeForFallback(cascade.path());

        RecognitionEventBus eventBus = new RecognitionEventBus();
        LiveRecognitionRuntime runtime = new LiveRecognitionRuntime(state, attendanceProperties, settings, eventBus);
        runtime.setStopSessionAction(() -> {
            try {
                stopSession(token);
            } catch (Exception ex) {
                logger.warn("Failed to stop session on demand: {}", ex.getMessage());
            }
        });
        SessionRuntime previous = activeSession.getAndSet(new SessionRuntime(state, runtime, eventBus, settings, this));
        if (previous != null) {
            previous.close();
        }

        logger.info("Started companion session {} (section={})", request.sessionId(), request.sectionId());
        return new StartSessionResponse(
                "session-started",
                state.sessionId(),
                state.sectionId(),
                model.path().toAbsolutePath().toString(),
                cascade.path().toAbsolutePath().toString(),
                labels.path().toAbsolutePath().toString(),
                totalBytes);
    }

    public SessionStatusResponse sessionStatus(String token) {
        if (token != null && !token.isBlank()) {
            verifyToken(token);
        }
        SessionRuntime runtime = activeSession.get();
        if (runtime == null) {
            return new SessionStatusResponse("idle", false, null, null, null, null, null, null, null, null, null, 0);
        }
        return runtime.state().toStatusResponse();
    }

    public synchronized StopSessionResponse stopSession(String token) {
        verifyToken(token);
        SessionRuntime runtime = activeSession.getAndSet(null);
        if (runtime == null) {
            return new StopSessionResponse("idle", null, "No active session to stop");
        }
        runtime.close();
        return new StopSessionResponse("stopped", runtime.state().sessionId(), "Session stopped");
    }

    public void verifyToken(String token) {
        if (handshakeToken == null || handshakeToken.isBlank()) {
            throw new CompanionHttpException(401, "Perform a handshake before starting a session");
        }
        if (token == null || token.isBlank() || !handshakeToken.equals(token.trim())) {
            throw new CompanionHttpException(401, "Invalid companion token");
        }
    }

    public void shutdown() {
        SessionRuntime runtime = activeSession.getAndSet(null);
        if (runtime != null) {
            runtime.close();
        }
        attendanceProperties.shutdown();
    }

    void handleAutoStop(SessionRuntime runtime, String reason) {
        if (runtime == null) {
            return;
        }
        activeSession.compareAndSet(runtime, null);
        try {
            runtime.close();
        } finally {
            String suffix = (reason != null && !reason.isBlank()) ? " (" + reason + ")" : "";
            logger.info("Auto-stopped companion session {}{}", runtime.state().sessionId(), suffix);
        }
    }

    private void cacheCascadeForFallback(Path cascadePath) {
        if (cascadePath == null || !Files.exists(cascadePath)) {
            return;
        }
        Path modelDir = attendanceProperties.directories().modelDir();
        if (modelDir == null) {
            return;
        }
        Path target = modelDir.resolve(cascadePath.getFileName());
        try {
            Files.createDirectories(modelDir);
            Files.copy(cascadePath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.warn("Unable to stage cascade fallback at {}: {}", target, ex.getMessage());
        }
    }

    public static String maskToken(String token) {
        if (StringUtils.isBlank(token)) {
            return "(absent)";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4);
    }

    private void writeSessionMetadata(SessionState state,
                                       ModelDownloader.FileDownloadResult model,
                                       ModelDownloader.FileDownloadResult cascade,
                                       ModelDownloader.FileDownloadResult labels) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", state.sessionId());
        metadata.put("sectionId", state.sectionId());
        metadata.put("startedAt", state.startedAt().toString());
        metadata.put("modelPath", model.path().toAbsolutePath().toString());
        metadata.put("modelChecksum", model.checksum());
        metadata.put("modelBytes", model.size());
        metadata.put("cascadePath", cascade.path().toAbsolutePath().toString());
        metadata.put("cascadeChecksum", cascade.checksum());
        metadata.put("cascadeBytes", cascade.size());
        metadata.put("labelsPath", labels.path().toAbsolutePath().toString());
        metadata.put("labelsChecksum", labels.checksum());
        metadata.put("labelsBytes", labels.size());
        metadata.put("missingStudentIds", state.missingStudentIds());
        metadata.put("labelMap", state.labelMap());
        metadata.put("downloadedBytes", model.size() + cascade.size() + labels.size());
        metadata.put("lastHeartbeat", state.lastHeartbeat().toString());
        metadata.put("scheduledStart", state.scheduledStart() != null ? state.scheduledStart().toString() : null);
        metadata.put("scheduledEnd", state.scheduledEnd() != null ? state.scheduledEnd().toString() : null);
        metadata.put("lateThresholdMinutes", state.lateThresholdMinutes());
        try {
            objectMapper.writeValue(state.sessionDirectory().resolve("session.json").toFile(), metadata);
        } catch (IOException ex) {
            logger.warn("Failed to write session metadata for {}: {}", state.sessionId(), ex.getMessage());
        }
    }

    private String resolveLabelsUrl(StartSessionRequest request) {
        if (hasText(request.labelsUrl())) {
            return request.labelsUrl().trim();
        }
        String modelUrl = request.modelUrl();
        if (!hasText(modelUrl)) {
            throw new CompanionHttpException(400, "Unable to derive labelsUrl from empty modelUrl");
        }
        if (modelUrl.endsWith("lbph.yml")) {
            return modelUrl.substring(0, modelUrl.length() - "lbph.yml".length()) + "labels.txt";
        }
        if (modelUrl.endsWith("lbph-model.yml")) {
            return modelUrl.substring(0, modelUrl.length() - "lbph-model.yml".length()) + "labels.txt";
        }
        return modelUrl + ".labels";
    }

    private void loadHandshakeToken() {
        if (!Files.exists(handshakeFile)) {
            return;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(handshakeFile.toFile(), Map.class);
            Object token = payload.get(TOKEN_KEY);
            if (token instanceof String s && !s.isBlank()) {
                this.handshakeToken = s;
            }
        } catch (IOException ex) {
            logger.warn("Unable to read companion handshake file '{}': {}", handshakeFile, ex.getMessage());
        }
    }

    private void persistHandshake(String token) {
        try {
            Files.createDirectories(handshakeFile.getParent());
            Map<String, Object> payload = new HashMap<>();
            payload.put(TOKEN_KEY, token);
            payload.put(ISSUED_AT_KEY, Instant.now().toString());
            objectMapper.writeValue(handshakeFile.toFile(), payload);
        } catch (IOException ex) {
            logger.warn("Failed to persist handshake token: {}", ex.getMessage());
        }
    }

    private void validateStartRequest(StartSessionRequest request) {
        if (request == null) {
            throw new CompanionHttpException(400, "Start session payload required");
        }
        if (!hasText(request.sessionId())) {
            throw new CompanionHttpException(400, "sessionId is required");
        }
        if (!hasText(request.modelUrl())) {
            throw new CompanionHttpException(400, "modelUrl is required");
        }
        if (!hasText(request.cascadeUrl())) {
            throw new CompanionHttpException(400, "cascadeUrl is required");
        }
        if (!hasText(request.authToken())) {
            throw new CompanionHttpException(401, "companion access token is required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeAccessToken(String token) {
        if (!hasText(token)) {
            throw new CompanionHttpException(401, "Companion access token missing");
        }
        return token.trim();
    }

    private String selectBackendBaseUrl(String requestedBaseUrl) {
        String override = sanitizeBackendBaseUrl(requestedBaseUrl);
        if (override != null) {
            return override;
        }
        String defaultBase = settings.backendBaseUrl();
        return sanitizeBackendBaseUrl(defaultBase);
    }

    private String sanitizeBackendBaseUrl(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
