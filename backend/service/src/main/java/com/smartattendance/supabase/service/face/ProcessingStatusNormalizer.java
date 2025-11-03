package com.smartattendance.supabase.service.face;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProcessingStatusNormalizer {

    private static final Set<String> ALLOWED_STATUSES = Set.of("pending", "processing", "completed", "failed");

    private ProcessingStatusNormalizer() {
    }

    public static NormalizationResult normalize(String requestedStatus) {
        String trimmed = requestedStatus != null ? requestedStatus.trim() : null;
        if (trimmed == null || trimmed.isEmpty()) {
            return new NormalizationResult("pending", requestedStatus, requestedStatus != null && !requestedStatus.isBlank());
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (ALLOWED_STATUSES.contains(lower)) {
            boolean changed = !trimmed.equalsIgnoreCase(lower);
            return new NormalizationResult(lower, requestedStatus, changed);
        }
        String normalized;
        if (lower.contains("fail") || lower.contains("error") || lower.contains("invalid")) {
            normalized = "failed";
        } else if (lower.contains("process")) {
            normalized = "processing";
        } else if (lower.contains("complete") || lower.contains("success") || lower.contains("ready")
                || lower.contains("train")) {
            normalized = "completed";
        } else {
            normalized = "pending";
        }
        return new NormalizationResult(normalized, requestedStatus, true);
    }

    public static String normalizeAndRecord(String requestedStatus, Map<String, Object> metadata) {
        NormalizationResult result = normalize(requestedStatus);
        if (result.changed() && metadata != null && result.original() != null && !result.original().isBlank()) {
            metadata.putIfAbsent("original_status", result.original());
        }
        return result.value();
    }

    public static boolean isAllowed(String status) {
        if (status == null) {
            return false;
        }
        return ALLOWED_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    public record NormalizationResult(String value, String original, boolean changed) {
    }
}
