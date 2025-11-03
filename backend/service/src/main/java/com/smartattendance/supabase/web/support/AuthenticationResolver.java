package com.smartattendance.supabase.web.support;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Helper that extracts common details from {@link Authentication} instances used by the Supabase REST API.
 */
@Component
public class AuthenticationResolver {

    /**
     * Resolve the authenticated user's identifier from the provided authentication token.
     *
     * @param authentication the current authentication context
     * @return an {@link Optional} containing the resolved UUID, or empty when the token does not expose a valid subject
     */
    public Optional<UUID> resolveUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String subject = token.getToken().getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(subject));
                } catch (IllegalArgumentException ignored) {
                    // Fall through to Optional.empty() below
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve the authenticated user's identifier, throwing when the token is missing or malformed.
     *
     * @param authentication the current authentication context
     * @return the resolved UUID
     * @throws AuthenticationSubjectMissingException when the authentication does not contain a valid subject
     */
    public UUID requireUserId(Authentication authentication) {
        return resolveUserId(authentication).orElseThrow(AuthenticationSubjectMissingException::new);
    }
}
