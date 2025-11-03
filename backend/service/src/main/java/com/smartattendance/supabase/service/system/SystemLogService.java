package com.smartattendance.supabase.service.system;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartattendance.supabase.dto.RecognitionLogEntryDto;

/**
 * Persists lightweight runtime logs and metadata without relying on Supabase.
 *
 * <p>The service keeps JSON artefacts under {@code runtime/logs}:
 * <ul>
 *     <li>{@code recognition-log.jsonl} - append-only recognition entries</li>
 *     <li>{@code session-actions/&lt;sessionId&gt;.jsonl} - session lifecycle history</li>
 *     <li>{@code training/&lt;studentId&gt;.json} - latest training snapshot</li>
 * </ul>
 */
@Service
public class SystemLogService {

    private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);

    private final ObjectMapper mapper;
    private final Path logsRoot;
    private final Path recognitionLogFile;
    private final Path sessionActionsDir;
    private final Path trainingDir;
    private final ReentrantLock recognitionLock = new ReentrantLock();
    private final ReentrantLock sessionLock = new ReentrantLock();

    public SystemLogService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.logsRoot = Paths.get("runtime", "logs").toAbsolutePath().normalize();
        this.recognitionLogFile = logsRoot.resolve("recognition-log.jsonl");
        this.sessionActionsDir = logsRoot.resolve("session-actions");
        this.trainingDir = logsRoot.resolve("training");
        initialiseStorage();
    }

    /** Records the outcome of a recognition attempt for later auditing. */
    public void recordRecognition(UUID sessionId,
                                  UUID studentId,
                                  Double confidence,
                                  boolean success,
                                  boolean requiresManualConfirmation,
                                  String type,
                                  String message,
                                  String trackId) {
        OffsetDateTime now = OffsetDateTime.now();
        ObjectNode node = mapper.createObjectNode();
        node.put("key", "recognition_log_" + now.toInstant().toEpochMilli());
        if (sessionId != null) {
            node.put("session_id", sessionId.toString());
        }
        if (studentId != null) {
            node.put("student_id", studentId.toString());
        }
        if (confidence != null && Double.isFinite(confidence) && confidence >= 0.0d) {
            node.put("confidence", confidence);
        } else {
            node.putNull("confidence");
        }
        node.put("success", success);
        node.put("requires_manual_confirmation", requiresManualConfirmation);
        if (type != null && !type.isBlank()) {
            node.put("type", type);
        }
        if (message != null && !message.isBlank()) {
            node.put("message", message);
        }
        if (trackId != null && !trackId.isBlank()) {
            node.put("track_id", trackId);
        }
        node.put("timestamp", now.toString());
        appendJsonLine(recognitionLogFile, node, recognitionLock);
    }

    /** Retrieves the most recent recognition log entries for the provided session. */
    public List<RecognitionLogEntryDto> fetchRecognitionLog(UUID sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        if (!Files.exists(recognitionLogFile)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(recognitionLogFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to read recognition log {}: {}", recognitionLogFile, ex.getMessage());
            return List.of();
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        List<RecognitionLogEntryDto> entries = new ArrayList<>();
        List<String> copy = new ArrayList<>(lines);
        Collections.reverse(copy);
        for (String line : copy) {
            if (entries.size() >= limit) {
                break;
            }
            String trimmed = line != null ? line.trim() : null;
            if (trimmed == null || trimmed.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = mapper.readTree(trimmed);
                RecognitionLogEntryDto dto = toRecognitionLogEntry(node);
                if (dto == null) {
                    continue;
                }
                if (sessionId != null && !sessionId.equals(dto.getSessionId())) {
                    continue;
                }
                entries.add(dto);
            } catch (IOException ex) {
                log.warn("Skipping malformed recognition log entry: {}", ex.getMessage());
            }
        }
        return entries;
    }

    /** Stores the latest training metadata snapshot for a student. */
    public void storeTrainingMetadata(UUID studentId, Object payload) {
        if (studentId == null || payload == null) {
            return;
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("student_id", studentId.toString());
        root.put("updated_at", OffsetDateTime.now().toString());
        root.set("data", mapper.valueToTree(payload));
        Path file = trainingDir.resolve(studentId + ".json");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, root);
        } catch (IOException ex) {
            log.warn("Failed to persist training metadata for {}: {}", studentId, ex.getMessage());
        }
    }

    /** Records a session lifecycle action such as start, pause or stop. */
    public void recordSessionAction(UUID sessionId, String action, UUID professorId, String status,
            OffsetDateTime startTime, OffsetDateTime endTime, OffsetDateTime timestamp) {
        if (sessionId == null) {
            return;
        }
        OffsetDateTime ts = timestamp != null ? timestamp : OffsetDateTime.now();
        ObjectNode node = mapper.createObjectNode();
        node.put("key", "session_log_" + sessionId + "_" + ts.toInstant().toEpochMilli());
        node.put("session_id", sessionId.toString());
        if (action != null) {
            node.put("action", action);
        }
        if (professorId != null) {
            node.put("professor_id", professorId.toString());
        }
        if (status != null) {
            node.put("status", status);
        }
        if (startTime != null) {
            node.put("start_time", startTime.toString());
        }
        if (endTime != null) {
            node.put("end_time", endTime.toString());
        }
        node.put("timestamp", ts.toString());
        Path file = sessionActionsDir.resolve(sessionId + ".jsonl");
        appendJsonLine(file, node, sessionLock);
    }

    private void initialiseStorage() {
        try {
            Files.createDirectories(logsRoot);
            Files.createDirectories(sessionActionsDir);
            Files.createDirectories(trainingDir);
        } catch (IOException ex) {
            log.warn("Failed to initialise runtime log directories {}: {}", logsRoot, ex.getMessage());
        }
    }

    private void appendJsonLine(Path file, ObjectNode node, ReentrantLock lock) {
        lock.lock();
        try {
            byte[] bytes = mapper.writeValueAsBytes(node);
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(file, System.lineSeparator(), StandardOpenOption.APPEND);
        } catch (IOException ex) {
            log.warn("Failed to append log entry to {}: {}", file, ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private RecognitionLogEntryDto toRecognitionLogEntry(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        RecognitionLogEntryDto dto = new RecognitionLogEntryDto();
        dto.setKey(node.path("key").asText(null));
        String sessionId = node.path("session_id").asText(null);
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                dto.setSessionId(UUID.fromString(sessionId));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        String studentId = node.path("student_id").asText(null);
        if (studentId != null && !studentId.isBlank()) {
            try {
                dto.setStudentId(UUID.fromString(studentId));
            } catch (IllegalArgumentException ignored) {
                // ignore invalid student id while keeping the rest of the payload
            }
        }
        if (node.has("confidence") && !node.get("confidence").isNull()) {
            dto.setConfidence(node.get("confidence").asDouble());
        }
        if (node.has("success")) {
            dto.setSuccess(node.get("success").asBoolean());
        }
        if (node.has("requires_manual_confirmation")) {
            dto.setRequiresManualConfirmation(node.get("requires_manual_confirmation").asBoolean());
        }
        if (node.has("type") && !node.get("type").isNull()) {
            dto.setType(node.get("type").asText(null));
        }
        if (node.has("message") && !node.get("message").isNull()) {
            dto.setMessage(node.get("message").asText(null));
        }
        if (node.has("track_id") && !node.get("track_id").isNull()) {
            dto.setTrackId(node.get("track_id").asText(null));
        }
        String timestamp = node.path("timestamp").asText(null);
        if (timestamp != null) {
            try {
                dto.setTimestamp(OffsetDateTime.parse(timestamp));
            } catch (Exception ignored) {
                dto.setTimestamp(null);
            }
        }
        return dto;
    }
}
