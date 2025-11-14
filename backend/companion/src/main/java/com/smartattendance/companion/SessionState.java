package com.smartattendance.companion;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SessionState {

    private final String sessionId;
    private final String sectionId;
    private final Path sessionDirectory;
    private final Instant startedAt;
    private final List<String> missingStudentIds;
    private final Map<String, String> labelMap;
    private final String companionToken;
    private final Instant scheduledStart;
    private final Instant scheduledEnd;
    private final int lateThresholdMinutes;
    private final String backendBaseUrl;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private volatile Instant lastHeartbeat;
    private volatile Path modelPath;
    private volatile Path cascadePath;
    private volatile Path labelsPath;
    private final AtomicLong downloadedBytes = new AtomicLong();

    public SessionState(String sessionId,
                        String sectionId,
                        Path sessionDirectory,
                        List<String> missingStudentIds,
                        Map<String, String> labelMap,
                        String companionToken,
                        String scheduledStartIso,
                        String scheduledEndIso,
                        Integer lateThresholdMinutes,
                        String backendBaseUrl) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.sectionId = sectionId;
        this.sessionDirectory = Objects.requireNonNull(sessionDirectory, "sessionDirectory");
        this.missingStudentIds = missingStudentIds != null
                ? List.copyOf(missingStudentIds)
                : List.of();
        this.labelMap = labelMap != null ? Map.copyOf(labelMap) : Map.of();
        this.companionToken = companionToken;
        this.startedAt = Instant.now();
        this.lastHeartbeat = this.startedAt;
        this.scheduledStart = parseInstant(scheduledStartIso);
        this.scheduledEnd = parseInstant(scheduledEndIso);
        this.lateThresholdMinutes = sanitizeLateThreshold(lateThresholdMinutes);
        this.backendBaseUrl = sanitizeBackendBaseUrl(backendBaseUrl);
    }

    public String sessionId() {
        return sessionId;
    }

    public String sectionId() {
        return sectionId;
    }

    public Path sessionDirectory() {
        return sessionDirectory;
    }

    public List<String> missingStudentIds() {
        return Collections.unmodifiableList(missingStudentIds);
    }

    public Map<String, String> labelMap() {
        return Collections.unmodifiableMap(labelMap);
    }

    public String companionToken() {
        return companionToken;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    public Path modelPath() {
        return modelPath;
    }

    public Path cascadePath() {
        return cascadePath;
    }

    public long downloadedBytes() {
        return downloadedBytes.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public Instant scheduledStart() {
        return scheduledStart;
    }

    public Instant scheduledEnd() {
        return scheduledEnd;
    }

    public int lateThresholdMinutes() {
        return lateThresholdMinutes;
    }

    public String backendBaseUrl() {
        return backendBaseUrl;
    }

    public void registerAssets(Path modelPath, Path cascadePath, Path labelsPath, long downloaded) {
        this.modelPath = modelPath;
        this.cascadePath = cascadePath;
        this.labelsPath = labelsPath;
        this.downloadedBytes.set(downloaded);
        touch();
    }

    public Path labelsPath() {
        return labelsPath;
    }

    public void touch() {
        this.lastHeartbeat = Instant.now();
    }

    public void markStopped() {
        active.set(false);
        touch();
    }

    public SessionStatusResponse toStatusResponse() {
        return new SessionStatusResponse(
                "ok",
                isActive(),
                sessionId,
                sectionId,
                modelPath != null ? modelPath.toAbsolutePath().toString() : null,
                cascadePath != null ? cascadePath.toAbsolutePath().toString() : null,
                labelsPath != null ? labelsPath.toAbsolutePath().toString() : null,
                startedAt.toString(),
                lastHeartbeat != null ? lastHeartbeat.toString() : null,
                scheduledStart != null ? scheduledStart.toString() : null,
                scheduledEnd != null ? scheduledEnd.toString() : null,
                lateThresholdMinutes
        );
    }

    public String resolveBackendBaseUrl(String fallback) {
        if (backendBaseUrl != null && !backendBaseUrl.isBlank()) {
            return backendBaseUrl;
        }
        return sanitizeBackendBaseUrl(fallback);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static int sanitizeLateThreshold(Integer value) {
        if (value == null) {
            return 15;
        }
        int sanitized = Math.max(0, value);
        return Math.min(sanitized, 240);
    }

    private static String sanitizeBackendBaseUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
