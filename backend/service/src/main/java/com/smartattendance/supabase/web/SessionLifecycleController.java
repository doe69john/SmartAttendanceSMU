package com.smartattendance.supabase.web;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.SessionActionRequest;
import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.service.session.SessionLifecycleService;
import com.smartattendance.supabase.service.session.SessionLifecycleService.Action;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Session Lifecycle", description = "Actions to control attendance session state")
public class SessionLifecycleController {

    private final SessionLifecycleService sessionLifecycleService;

    public SessionLifecycleController(SessionLifecycleService sessionLifecycleService) {
        this.sessionLifecycleService = sessionLifecycleService;
    }

    @PostMapping("/{id}/{action}")
    @Operation(summary = "Manage session lifecycle", description = "Transitions a session by invoking start, pause, resume, or stop actions.")
    public ResponseEntity<SessionDetailsDto> manage(
            @PathVariable("id") UUID sessionId,
            @PathVariable("action") String action,
            @RequestBody(required = false) SessionActionRequest body) {
        UUID professorId = body != null ? body.getProfessorId() : null;
        Action parsed = Action.valueOf(action.toLowerCase(Locale.ROOT));
        SessionDetailsDto updated = sessionLifecycleService.handleSessionAction(sessionId, parsed, professorId);
        return ResponseEntity.ok(updated);
    }
}
