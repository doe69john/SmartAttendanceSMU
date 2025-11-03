package com.smartattendance.supabase.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.smartattendance.config.PublicApiEndpoints;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SupabaseSessionCookieFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final SupabaseSessionCookieService cookieService;
    private final List<RequestMatcher> publicRequestMatchers;
    private final boolean allowCookieAuthorization;

    public SupabaseSessionCookieFilter(SupabaseSessionCookieService cookieService,
                                       @Value("${app.security.enable-cookie-authorization:false}") boolean allowCookieAuthorization) {
        this.cookieService = cookieService;
        this.allowCookieAuthorization = allowCookieAuthorization;
        this.publicRequestMatchers = Arrays.stream(PublicApiEndpoints.API_PATTERNS)
                .map(pattern -> (RequestMatcher) new AntPathRequestMatcher(pattern))
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!allowCookieAuthorization) {
            return true;
        }
        String servletPath = request.getServletPath();
        if (!StringUtils.hasText(servletPath)) {
            servletPath = "/";
        }
        if (!servletPath.startsWith(API_PREFIX)) {
            return true;
        }
        return publicRequestMatchers.stream().anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String existingAuthorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(existingAuthorization)) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> accessToken = cookieService.readAccessToken(request);
        if (accessToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpServletRequestWrapper wrapper = new AuthorizationRequestWrapper(request, accessToken.get());
        filterChain.doFilter(wrapper, response);
    }

    private static class AuthorizationRequestWrapper extends HttpServletRequestWrapper {

        private final String bearerToken;

        AuthorizationRequestWrapper(HttpServletRequest request, String accessToken) {
            super(request);
            this.bearerToken = "Bearer " + accessToken;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return bearerToken;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(bearerToken));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>();
            Enumeration<String> existing = super.getHeaderNames();
            while (existing.hasMoreElements()) {
                names.add(existing.nextElement());
            }
            names.add(HttpHeaders.AUTHORIZATION);
            return Collections.enumeration(names);
        }
    }
}
