package com.smartattendance.config;

/**
 * Shared definitions of publicly accessible API endpoints. These are used by both
 * the security configuration and filters so that updates remain centralized.
 */
public final class PublicApiEndpoints {

    public static final String[] API_PATTERNS = {
            "/api/public/**",
            "/api/auth/**",
            "/api/settings/**"
    };

    private PublicApiEndpoints() {
        // Utility class
    }
}
