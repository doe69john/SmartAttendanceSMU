package com.smartattendance.supabase.web;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.SessionActionRequest;
import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.service.session.SessionLifecycleService;
import com.smartattendance.supabase.service.session.SessionLifecycleService.Action;
import com.smartattendance.supabase.web.support.BackendBaseUrlResolver;

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Session Lifecycle", description = "Actions to control attendance session state")
public class SessionLifecycleController {

    private final SessionLifecycleService sessionLifecycleService;
    private final BackendBaseUrlResolver backendBaseUrlResolver;

    public SessionLifecycleController(SessionLifecycleService sessionLifecycleService,
                                     BackendBaseUrlResolver backendBaseUrlResolver) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.backendBaseUrlResolver = backendBaseUrlResolver;
    }

    @PostMapping("/{id}/{action}")
    @Operation(summary = "Manage session lifecycle", description = "Transitions a session by invoking start, pause, resume, or stop actions.")
    public ResponseEntity<SessionDetailsDto> manage(
            @PathVariable("id") UUID sessionId,
            @PathVariable("action") String action,
            @RequestBody(required = false) SessionActionRequest body,
            HttpServletRequest request) {
        UUID professorId = body != null ? body.getProfessorId() : null;
        Action parsed = Action.valueOf(action.toLowerCase(Locale.ROOT));
        SessionDetailsDto updated = sessionLifecycleService.handleSessionAction(sessionId, parsed, professorId);
        if (updated != null) {
            updated.setBackendBaseUrl(backendBaseUrlResolver.resolve(request));
        }
        return ResponseEntity.ok(updated);
    }
}
