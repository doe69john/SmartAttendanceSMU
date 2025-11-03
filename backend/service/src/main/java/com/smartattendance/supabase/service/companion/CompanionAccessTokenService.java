package com.smartattendance.supabase.service.companion;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.smartattendance.supabase.auth.OutboundAuth;

/**
 * Issues and validates short-lived bearer tokens that allow the native companion application to
 * download session-specific assets from the backend. Tokens are stored in-memory and expire
 * automatically after a configurable duration.
 */
@Service
public class CompanionAccessTokenService {

    private static final Logger logger = LoggerFactory.getLogger(CompanionAccessTokenService.class);

    private final ConcurrentMap<String, CompanionToken> activeTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration tokenTtl;
    public CompanionAccessTokenService(@Value("${companion.token.ttl-minutes:60}") long ttlMinutes) {
        this.tokenTtl = Duration.ofMinutes(Math.max(ttlMinutes, 1));
    }

    public IssuedToken issueToken(UUID professorProfileId, UUID sectionId, Jwt supabaseJwt,
            Collection<? extends GrantedAuthority> authorities) {
        Objects.requireNonNull(professorProfileId, "professorProfileId");
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(authorities, "authorities");
        if (supabaseJwt == null) {
            throw new IllegalArgumentException("Supabase JWT is required to issue a companion token");
        }
        OutboundAuth.ensureBearerFormat(supabaseJwt.getTokenValue());
        purgeExpiredTokens();
        String token = generateToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(tokenTtl);
        activeTokens.put(token, new CompanionToken(
                token,
                professorProfileId,
                sectionId,
                expiresAt,
                supabaseJwt,
                List.copyOf(authorities)));
        logger.info("Issued companion token expiring at {} for section {} and professor {}", expiresAt, sectionId,
                professorProfileId);
        return new IssuedToken(token, expiresAt);
    }

    public boolean validateToken(String tokenValue, UUID sectionId) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        CompanionToken token = activeTokens.get(tokenValue);
        if (token == null) {
            return false;
        }
        if (token.expiresAt().isBefore(OffsetDateTime.now())) {
            activeTokens.remove(tokenValue);
            return false;
        }
        if (sectionId != null && !sectionId.equals(token.sectionId())) {
            return false;
        }
        return true;
    }

    public Optional<CompanionToken> lookup(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        CompanionToken token = activeTokens.get(tokenValue);
        if (token == null) {
            return Optional.empty();
        }
        if (token.expiresAt().isBefore(OffsetDateTime.now())) {
            activeTokens.remove(tokenValue);
            return Optional.empty();
        }
        return Optional.of(token);
    }

    public void revoke(String tokenValue) {
        if (tokenValue != null) {
            activeTokens.remove(tokenValue);
        }
    }

    public void revokeTokensForSection(UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        activeTokens.entrySet().removeIf(entry -> sectionId.equals(entry.getValue().sectionId()));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void purgeExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now();
        activeTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record CompanionToken(
            String value,
            UUID professorProfileId,
            UUID sectionId,
            OffsetDateTime expiresAt,
            Jwt supabaseJwt,
            List<? extends GrantedAuthority> authorities) {
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }
}
