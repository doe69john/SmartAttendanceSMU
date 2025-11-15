package com.smartattendance.companion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smartattendance.companion.recognition.LiveRecognitionRuntime;
import com.smartattendance.companion.recognition.RecognitionEventBus;

public final class SessionRuntime implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SessionRuntime.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final Duration AUTO_STOP_POLL_INTERVAL = Duration.ofSeconds(30);

    private final SessionState state;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> autoStopTask;
    private final LiveRecognitionRuntime recognitionRuntime;
    private final RecognitionEventBus eventBus;
    private final BackendEventForwarder backendForwarder;
    private final CompanionSettings settings;
    private final CompanionSessionManager manager;
    private final HttpClient backendClient;
    private final AtomicBoolean autoStopTriggered = new AtomicBoolean(false);

    public SessionRuntime(SessionState state,
                         LiveRecognitionRuntime recognitionRuntime,
                         RecognitionEventBus eventBus,
                         CompanionSettings settings,
                         CompanionSessionManager manager) {
        this.state = state;
        this.recognitionRuntime = recognitionRuntime;
        this.eventBus = eventBus;
        this.settings = settings;
        this.manager = manager;
        this.backendClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "companion-session-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeatTask = scheduler.scheduleAtFixedRate(state::touch,
                HEARTBEAT_INTERVAL.toSeconds(),
                HEARTBEAT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
        this.backendForwarder = new BackendEventForwarder(settings, state, eventBus);
        if (recognitionRuntime != null) {
            recognitionRuntime.start();
        }
        scheduleAutoStopIfNecessary();
    }

    public SessionState state() {
        return state;
    }

    public RecognitionEventBus eventBus() {
        return eventBus;
    }

    @Override
    public void close() {
        state.markStopped();
        cancelHeartbeat();
        cancelAutoStopTask();
        scheduler.shutdownNow();
        if (recognitionRuntime != null) {
            recognitionRuntime.close();
        }
        backendForwarder.close();
        logger.info("Stopped companion session {}", state.sessionId());
    }

    void requestAutoStop(String reason) {
        if (!autoStopTriggered.compareAndSet(false, true)) {
            return;
        }
        cancelAutoStopTask();
        logger.info("Auto-stop triggered for session {}: {}", state.sessionId(), reason);
        try {
            notifyBackendStop();
        } catch (Exception ex) {
            logger.warn("Failed to notify backend of auto-stop: {}", ex.getMessage());
        } finally {
            manager.handleAutoStop(this, reason);
        }
    }

    private void scheduleAutoStopIfNecessary() {
        Instant scheduledEnd = state.scheduledEnd();
        if (scheduledEnd == null) {
            return;
        }
        long initialDelayMillis = Duration.between(Instant.now(), scheduledEnd).toMillis();
        if (initialDelayMillis < 0) {
            initialDelayMillis = 0;
        }
        autoStopTask = scheduler.scheduleAtFixedRate(() -> {
            if (!state.isActive()) {
                cancelAutoStopTask();
                return;
            }
            Instant now = Instant.now();
            if (now.isBefore(scheduledEnd)) {
                return;
            }
            requestAutoStop("Scheduled end reached");
        }, initialDelayMillis, Math.max(5000L, AUTO_STOP_POLL_INTERVAL.toMillis()), TimeUnit.MILLISECONDS);
    }

    private void notifyBackendStop() throws Exception {
        if (settings == null) {
            return;
        }
        String baseUrl = resolveBackendBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        String sessionId = state.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sessions/" + sessionId + "/stop"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"stop\"}"));
        String authToken = resolveAuthorizationToken();
        if (!authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }
        String companionToken = state.companionToken();
        if (companionToken != null && !companionToken.isBlank()) {
            builder.header("X-Companion-Token", companionToken.trim());
        }
        backendClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private String resolveAuthorizationToken() {
        if (settings != null) {
            String serviceToken = settings.serviceToken();
            if (serviceToken != null && !serviceToken.isBlank()) {
                return serviceToken.trim();
            }
        }
        String companionToken = state.companionToken();
        return companionToken != null ? companionToken.trim() : "";
    }

    private void cancelAutoStopTask() {
        ScheduledFuture<?> task = autoStopTask;
        if (task != null) {
            task.cancel(true);
        }
        autoStopTask = null;
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
    }

    private String resolveBackendBaseUrl() {
        if (state != null && state.backendBaseUrl() != null && !state.backendBaseUrl().isBlank()) {
            return state.backendBaseUrl();
        }
        if (settings != null && settings.backendBaseUrl() != null && !settings.backendBaseUrl().isBlank()) {
            return settings.backendBaseUrl();
        }
        return null;
    }
}
