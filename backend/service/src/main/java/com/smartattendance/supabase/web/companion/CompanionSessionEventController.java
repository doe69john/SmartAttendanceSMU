package com.smartattendance.supabase.web.companion;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartattendance.supabase.dto.events.RecognitionEvent;
import com.smartattendance.supabase.service.session.SessionEventPublisher;
import com.smartattendance.supabase.service.system.SystemLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/companion")
@Tag(name = "Companion Session Events", description = "Forward recognition events from the native companion app")
public class CompanionSessionEventController {

    private final SessionEventPublisher eventPublisher;
    private final SystemLogService systemLogService;

    public CompanionSessionEventController(SessionEventPublisher eventPublisher,
                                           SystemLogService systemLogService) {
        this.eventPublisher = eventPublisher;
        this.systemLogService = systemLogService;
    }

    @PostMapping("/sections/{sectionId}/sessions/{sessionId}/recognition-events")
    @Operation(summary = "Forward recognition event", description = "Publishes a recognition event from the companion app to live SSE subscribers.")
    public ResponseEntity<Void> publishRecognitionEvent(@PathVariable("sectionId") UUID sectionId,
                                                        @PathVariable("sessionId") UUID sessionId,
                                                        @RequestBody CompanionRecognitionEventRequest request) {
        RecognitionEvent event = new RecognitionEvent();
        UUID studentUuid = null;
        String studentId = request.getStudentId();
        if (studentId != null && !studentId.isBlank()) {
            try {
                studentUuid = UUID.fromString(studentId.trim());
                event.setStudentId(studentUuid);
            } catch (IllegalArgumentException ignored) {
                // leave null if not a UUID
            }
        }
        Double confidence = request.getConfidence();
        event.setConfidence(confidence);
        boolean success = request.isSuccess();
        boolean requiresManual = request.isRequiresManualConfirmation();
        event.setSuccess(success);
        event.setRequiresManualConfirmation(requiresManual);
        String type = request.getType();
        if (type != null && type.isBlank()) {
            type = null;
        }
        event.setType(type);
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            message = success
                    ? (requiresManual ? "Manual confirmation accepted" : "Recognition succeeded")
                    : "Recognition failed";
        }
        event.setMessage(message);
        String trackId = request.getTrackId();
        if (trackId != null && trackId.isBlank()) {
            trackId = null;
        }
        event.setTrackId(trackId);
        OffsetDateTime timestamp = request.getTimestamp() != null ? request.getTimestamp() : OffsetDateTime.now();
        event.setTimestamp(timestamp);
        eventPublisher.publish(sessionId, "recognition", event);
        systemLogService.recordRecognition(
                sessionId,
                studentUuid,
                confidence,
                success,
                requiresManual,
                type,
                message,
                trackId);
        return ResponseEntity.accepted().build();
    }
}
