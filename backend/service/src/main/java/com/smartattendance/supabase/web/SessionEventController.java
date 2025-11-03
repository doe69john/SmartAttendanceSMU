package com.smartattendance.supabase.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.service.session.SessionEventPublisher;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Session Events", description = "Server-sent event streams for live sessions")
public class SessionEventController {

    private final SessionEventPublisher eventPublisher;

    public SessionEventController(SessionEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Subscribe to session events", description = "Creates a server-sent events stream for session updates.")
    public SseEmitter events(@PathVariable("id") UUID sessionId) {
        return eventPublisher.createEmitter(sessionId);
    }
}
