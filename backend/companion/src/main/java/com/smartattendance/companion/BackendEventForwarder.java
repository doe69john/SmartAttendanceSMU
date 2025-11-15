package com.smartattendance.companion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartattendance.companion.recognition.RecognitionEvent;
import com.smartattendance.companion.recognition.RecognitionEventBus;
import com.smartattendance.companion.recognition.RecognitionEventType;

final class BackendEventForwarder implements Consumer<RecognitionEvent>, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(BackendEventForwarder.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final CompanionSettings settings;
    private final SessionState state;
    private final RecognitionEventBus eventBus;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "companion-event-forwarder");
        thread.setDaemon(true);
        return thread;
    });
    private final Consumer<RecognitionEvent> subscriber = this::accept;

    BackendEventForwarder(CompanionSettings settings, SessionState state, RecognitionEventBus eventBus) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.state = Objects.requireNonNull(state, "state");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        eventBus.subscribe(subscriber);
    }

    @Override
    public void accept(RecognitionEvent event) {
        if (!shouldForward(event)) {
            return;
        }
        executor.submit(() -> forward(event));
    }

    private boolean shouldForward(RecognitionEvent event) {
        if (event == null) {
            return false;
        }
        if (state.sectionId() == null || state.sectionId().isBlank()) {
            return false;
        }
        String backendBaseUrl = resolveBackendBaseUrl();
        if (backendBaseUrl == null || backendBaseUrl.isBlank()) {
            return false;
        }
        String token = resolveToken();
        if (token == null || token.isBlank()) {
            return false;
        }
        RecognitionEventType type = event.getType();
        if (type == null) {
            return false;
        }
        // Only forward events that are relevant to the dashboard.
        return switch (type) {
            case ATTENDANCE_RECORDED,
                    MANUAL_CONFIRMED,
                    MANUAL_REJECTED,
                    AUTO_REJECTED,
                    ATTENDANCE_SKIPPED,
                    ERROR -> true;
            default -> false;
        };
    }

    private void forward(RecognitionEvent event) {
        try {
            String token = resolveToken();
            if (token == null || token.isBlank()) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildEndpoint()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(serialize(event)))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.debug("Backend recognition forward failed: HTTP {}", response.statusCode());
            }
        } catch (IOException ex) {
            logger.debug("Failed to forward recognition event: {}", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String serialize(RecognitionEvent event) throws IOException {
        ObjectNode node = objectMapper.createObjectNode();
        if (event.getStudentId() != null && !event.getStudentId().isBlank()) {
            node.put("studentId", event.getStudentId());
        }
        if (Double.isFinite(event.getConfidence())) {
            node.put("confidence", event.getConfidence());
        }
        node.put("success", event.isSuccess());
        node.put("requiresManualConfirmation", event.isManual());
        Instant timestamp = event.getTimestamp();
        node.put("timestamp", timestamp != null ? DateTimeFormatter.ISO_INSTANT.format(timestamp) : DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        node.put("type", event.getType().name().toLowerCase());
        if (event.getMessage() != null) {
            node.put("message", event.getMessage());
        }
        if (event.getTrackId() != null) {
            node.put("trackId", event.getTrackId());
        }
        return objectMapper.writeValueAsString(node);
    }

    private String buildEndpoint() {
        String backendBaseUrl = resolveBackendBaseUrl();
        if (backendBaseUrl == null || backendBaseUrl.isBlank()) {
            throw new IllegalStateException("Backend base URL is not configured");
        }
        return backendBaseUrl
                + "/companion/sections/"
                + state.sectionId()
                + "/sessions/"
                + state.sessionId()
                + "/recognition-events";
    }

    private String resolveBackendBaseUrl() {
        return state.resolveBackendBaseUrl(settings.backendBaseUrl());
    }

    private String resolveToken() {
        String token = state.companionToken();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return settings.serviceToken() != null ? settings.serviceToken().trim() : "";
    }

    @Override
    public void close() {
        eventBus.unsubscribe(subscriber);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
