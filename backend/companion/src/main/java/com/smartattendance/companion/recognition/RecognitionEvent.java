package com.smartattendance.companion.recognition;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable payload describing a recognition lifecycle event.
 */
public final class RecognitionEvent {

    private final RecognitionEventType type;
    private final Instant timestamp;
    private final String trackId;
    private final String studentId;
    private final String studentName;
    private final double confidence;
    private final String message;
    private final boolean success;
    private final boolean manual;

    public RecognitionEvent(RecognitionEventType type,
                            Instant timestamp,
                            String trackId,
                            String studentId,
                            String studentName,
                            double confidence,
                            String message,
                            boolean success,
                            boolean manual) {
        this.type = Objects.requireNonNull(type, "type");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.trackId = trackId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.confidence = confidence;
        this.message = message;
        this.success = success;
        this.manual = manual;
    }

    public RecognitionEventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isManual() {
        return manual;
    }
}
