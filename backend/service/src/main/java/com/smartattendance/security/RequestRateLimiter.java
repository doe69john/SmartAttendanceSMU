package com.smartattendance.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Minimal fixed-window rate limiter used to throttle sensitive endpoints without adding external dependencies.
 */
@Component
public class RequestRateLimiter {

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

    public void assertAllowed(String bucket, HttpServletRequest request, Duration window, int limit) {
        Objects.requireNonNull(request, "request must not be null");
        if (!tryConsume(buildKey(bucket, request), window, limit)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests from this client. Please wait and try again.");
        }
    }

    boolean tryConsume(String key, Duration window, int limit) {
        Objects.requireNonNull(key, "rate limiter key must not be null");
        if (limit <= 0) {
            return false;
        }
        long windowSeconds = Math.max(window.toSeconds(), 1);
        long windowId = Instant.now().getEpochSecond() / windowSeconds;
        AtomicBoolean allowed = new AtomicBoolean(false);

        counters.compute(key, (k, previous) -> {
            if (previous == null || previous.windowId() != windowId) {
                allowed.set(true);
                return new Counter(windowId, 1);
            }
            if (previous.count() >= limit) {
                allowed.set(false);
                return previous;
            }
            allowed.set(true);
            return new Counter(previous.windowId(), previous.count() + 1);
        });

        return allowed.get();
    }

    private String buildKey(String bucket, HttpServletRequest request) {
        StringBuilder builder = new StringBuilder(64);
        builder.append(StringUtils.hasText(bucket) ? bucket : "default");
        builder.append(':');
        builder.append(resolveClientAddress(request));
        return builder.toString();
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int separator = forwarded.indexOf(',');
            return separator >= 0 ? forwarded.substring(0, separator).trim() : forwarded.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        if (StringUtils.hasText(remoteAddr)) {
            return remoteAddr;
        }
        return "unknown";
    }

    private record Counter(long windowId, int count) {
    }
}
