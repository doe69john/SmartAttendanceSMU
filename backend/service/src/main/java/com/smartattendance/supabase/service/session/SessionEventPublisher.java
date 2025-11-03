package com.smartattendance.supabase.service.session;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.smartattendance.supabase.dto.events.SessionConnectionEvent;

@Component
public class SessionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SessionEventPublisher.class);
    private static final long DEFAULT_TIMEOUT = Duration.ofMinutes(5).toMillis();

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("init").data(new SessionConnectionEvent("connected")));
        } catch (IOException e) {
            log.debug("Unable to send initial SSE event: {}", e.getMessage());
        }
        return emitter;
    }

    public void publish(UUID sessionId, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters == null || sessionEmitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : sessionEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                removeEmitter(sessionId, emitter);
            }
        }
    }

    public void complete(UUID sessionId) {
        CopyOnWriteArrayList<SseEmitter> sessionEmitters = emitters.remove(sessionId);
        if (sessionEmitters == null) {
            return;
        }
        for (SseEmitter emitter : sessionEmitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private void removeEmitter(UUID sessionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters == null) {
            return;
        }
        sessionEmitters.remove(emitter);
        if (sessionEmitters.isEmpty()) {
            emitters.remove(sessionId);
        }
    }
}
