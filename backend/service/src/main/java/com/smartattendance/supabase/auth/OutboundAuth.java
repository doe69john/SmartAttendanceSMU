package com.smartattendance.supabase.auth;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/**
 * Utility methods for propagating the caller's Supabase authentication to
 * outbound requests.
 */
public final class OutboundAuth {

    private static final String BEARER_PREFIX = "Bearer ";

    private OutboundAuth() {
    }

    /**
     * Resolve the current request's bearer token, failing fast if none is
     * available.
     *
     * @return the Authorization header value in the form {@code Bearer <token>}
     */
    public static String requireBearerToken() {
        return resolveBearerToken().orElseThrow(() ->
                new IllegalStateException("No authenticated Supabase session available for outbound request"));
    }

    /**
     * Resolve the current request's bearer token if one is available.
     *
     * @return an optional containing the Authorization header value
     */
    public static Optional<String> resolveBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            String tokenValue = jwt.getToken().getTokenValue();
            if (StringUtils.hasText(tokenValue)) {
                return Optional.of(ensureBearerFormat(tokenValue));
            }
        }
        return Optional.empty();
    }

    /**
     * Ensures the supplied token value is formatted as a Bearer Authorization
     * header.
     *
     * @param token raw token or header value
     * @return the normalized Authorization header value
     */
    public static String ensureBearerFormat(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Bearer token must not be blank");
        }
        String trimmed = token.trim();
        if (trimmed.length() >= BEARER_PREFIX.length()
                && trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String remainder = trimmed.substring(BEARER_PREFIX.length()).trim();
            if (!StringUtils.hasText(remainder)) {
                throw new IllegalArgumentException("Bearer token must not be blank");
            }
            return BEARER_PREFIX + remainder;
        }
        return BEARER_PREFIX + trimmed;
    }
}
