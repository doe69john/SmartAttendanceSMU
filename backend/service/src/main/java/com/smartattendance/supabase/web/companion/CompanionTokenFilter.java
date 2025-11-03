package com.smartattendance.supabase.web.companion;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.smartattendance.supabase.service.companion.CompanionAccessTokenService;
import com.smartattendance.supabase.service.companion.CompanionAccessTokenService.CompanionToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class CompanionTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CompanionTokenFilter.class);

    private final CompanionAccessTokenService tokenService;

    public CompanionTokenFilter(CompanionAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/companion/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (path.equals("/api/companion/access-token") || path.startsWith("/api/companion/releases")) {
            filterChain.doFilter(request, response);
            return;
        }
        String tokenValue = resolveToken(request);
        UUID sectionId = resolveSectionId(path);

        CompanionToken token = tokenService.lookup(tokenValue)
                .filter(stored -> sectionId == null || sectionId.equals(stored.sectionId()))
                .orElse(null);

        if (token == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":"error","message":"Invalid or expired companion access token"}
                    """);
            log.info("Rejected companion request for path {} due to missing/expired token", path);
            return;
        }
        Jwt supabaseJwt = token.supabaseJwt();
        if (supabaseJwt == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":"error","message":"Companion token missing delegated Supabase session; please reissue"}
                    """);
            log.warn("Rejected companion request for path {} because delegated credentials are absent (token={}, section={})",
                    path, maskToken(token.value()), token.sectionId());
            return;
        }

        Instant jwtExpiry = supabaseJwt.getExpiresAt();
        if (jwtExpiry != null && jwtExpiry.isBefore(Instant.now())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":"error","message":"Your Supabase session expired. Please reauthenticate and retry."}
                    """);
            log.warn("Rejected companion request for path {} due to expired delegated Supabase JWT (token={}, section={})",
                    path, maskToken(token.value()), token.sectionId());
            return;
        }

        HttpServletRequest sanitized = new AuthorizationHeaderStrippingRequest(request);
        log.info("Validated companion token for section {} on path {}", token.sectionId(), path);
        SecurityContext originalContext = SecurityContextHolder.getContext();
        SecurityContext delegatedContext = SecurityContextHolder.createEmptyContext();
        delegatedContext.setAuthentication(new JwtAuthenticationToken(supabaseJwt, token.authorities()));
        SecurityContextHolder.setContext(delegatedContext);
        try {
            filterChain.doFilter(sanitized, response);
        } finally {
            SecurityContextHolder.setContext(originalContext);
            log.debug("Cleared delegated Supabase context for companion path {}", path);
        }
    }

    private String maskToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return "(absent)";
        }
        String trimmed = tokenValue.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String fallback = request.getHeader("X-Companion-Token");
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private UUID resolveSectionId(String path) {
        if (!path.startsWith("/api/companion/sections/")) {
            return null;
        }
        String remainder = path.substring("/api/companion/sections/".length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0) {
            return null;
        }
        String sectionPart = remainder.substring(0, slashIndex);
        try {
            return UUID.fromString(sectionPart);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static final class AuthorizationHeaderStrippingRequest extends HttpServletRequestWrapper {

        AuthorizationHeaderStrippingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Enumeration<String> base = super.getHeaderNames();
            if (base == null) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(Collections.list(base).stream()
                    .filter(header -> !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(header))
                    .toList());
        }
    }

}
