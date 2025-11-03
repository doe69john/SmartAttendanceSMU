package com.smartattendance.supabase.auth;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.smartattendance.supabase.dto.auth.SupabaseAuthSession;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SupabaseSessionCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "sb-access-token";
    public static final String REFRESH_TOKEN_COOKIE = "sb-refresh-token";

    private final SupabaseGoTrueProperties properties;

    public SupabaseSessionCookieService(SupabaseGoTrueProperties properties) {
        this.properties = properties;
    }

    public void storeSession(HttpServletResponse response, SupabaseAuthSession session) {
        if (session == null) {
            return;
        }
        String accessToken = session.getAccessToken();
        if (StringUtils.hasText(accessToken)) {
            long maxAge = session.getExpiresIn() != null ? session.getExpiresIn() : 3600;
            ResponseCookie accessCookie = buildCookie(ACCESS_TOKEN_COOKIE, accessToken,
                    Duration.ofSeconds(Math.max(maxAge, 0))).build();
            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        }

        String refreshToken = session.getRefreshToken();
        if (StringUtils.hasText(refreshToken)) {
            long days = Math.max(properties.getRefreshTokenTtlDays(), 1);
            ResponseCookie refreshCookie = buildCookie(REFRESH_TOKEN_COOKIE, refreshToken,
                    Duration.ofDays(days)).build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
    }

    public void clearSession(HttpServletResponse response) {
        ResponseCookie accessCookie = buildCookie(ACCESS_TOKEN_COOKIE, "", Duration.ZERO).maxAge(0).build();
        ResponseCookie refreshCookie = buildCookie(REFRESH_TOKEN_COOKIE, "", Duration.ZERO).maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder buildCookie(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(resolveSameSite())
                .path(properties.getCookiePath())
                .maxAge(maxAge);
        if (StringUtils.hasText(properties.getCookieDomain())) {
            builder.domain(properties.getCookieDomain());
        }
        return builder;
    }

    private String resolveSameSite() {
        String sameSite = properties.getCookieSameSite();
        if (!StringUtils.hasText(sameSite)) {
            return "Lax";
        }
        String normalized = sameSite.trim();
        if ("None".equalsIgnoreCase(normalized)) {
            return "Lax";
        }
        return normalized;
    }
}
