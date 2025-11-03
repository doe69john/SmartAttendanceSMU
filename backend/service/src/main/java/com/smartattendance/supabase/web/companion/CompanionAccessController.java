package com.smartattendance.supabase.web.companion;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.util.StringUtils;

import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.entity.SectionEntity;
import com.smartattendance.supabase.repository.ProfileRepository;
import com.smartattendance.supabase.repository.SectionRepository;
import com.smartattendance.supabase.service.companion.CompanionAccessTokenService;
import com.smartattendance.supabase.service.companion.CompanionAccessTokenService.IssuedToken;
import com.smartattendance.supabase.auth.SupabaseSessionCookieService;
import com.smartattendance.supabase.web.support.AuthenticationResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/companion")
@Tag(name = "Companion Access", description = "Issue access tokens for the native companion application")
public class CompanionAccessController {

    private static final Logger log = LoggerFactory.getLogger(CompanionAccessController.class);

    private final CompanionAccessTokenService tokenService;
    private final AuthenticationResolver authenticationResolver;
    private final ProfileRepository profileRepository;
    private final SectionRepository sectionRepository;
    private final JwtDecoder jwtDecoder;
    private final SupabaseSessionCookieService cookieService;

    public CompanionAccessController(CompanionAccessTokenService tokenService,
                                     AuthenticationResolver authenticationResolver,
                                     ProfileRepository profileRepository,
                                     SectionRepository sectionRepository,
                                     JwtDecoder jwtDecoder,
                                     SupabaseSessionCookieService cookieService) {
        this.tokenService = tokenService;
        this.authenticationResolver = authenticationResolver;
        this.profileRepository = profileRepository;
        this.sectionRepository = sectionRepository;
        this.jwtDecoder = jwtDecoder;
        this.cookieService = cookieService;
    }

    @PostMapping("/access-token")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Issue companion access token",
            description = "Returns a short-lived token that authenticates the companion app when downloading assets.")
    public ResponseEntity<?> issueToken(@RequestBody CompanionAccessTokenRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        UUID userId = authenticationResolver.requireUserId(authentication);
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Profile not found for authenticated user"));
        if (profile.getRole() != ProfileEntity.Role.professor && profile.getRole() != ProfileEntity.Role.admin) {
            return ResponseEntity.status(403).build();
        }
        UUID sectionId = request.sectionId();
        SectionEntity section = sectionRepository.findById(sectionId)
                .orElse(null);
        if (section == null) {
            return ResponseEntity.notFound().build();
        }
        if (section.getProfessorId() != null
                && !section.getProfessorId().equals(profile.getId())
                && profile.getRole() != ProfileEntity.Role.admin) {
            return ResponseEntity.status(403).build();
        }
        JwtAuthenticationToken jwtAuthentication =
                authentication instanceof JwtAuthenticationToken jwt ? jwt : null;
        Jwt supabaseJwt = jwtAuthentication != null ? jwtAuthentication.getToken() : null;
        if (supabaseJwt == null) {
            String authorization = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String tokenValue = authorization.substring(7).trim();
                if (StringUtils.hasText(tokenValue)) {
                    try {
                        supabaseJwt = jwtDecoder.decode(tokenValue);
                    } catch (JwtException ex) {
                        log.warn("Failed to decode Supabase JWT from Authorization header for section {}: {}", sectionId,
                                ex.getMessage());
                    }
                }
            }
        }
        if (supabaseJwt == null) {
            Optional<String> cookieToken = cookieService.readAccessToken(servletRequest);
            if (cookieToken.isPresent()) {
                try {
                    supabaseJwt = jwtDecoder.decode(cookieToken.get());
                } catch (JwtException ex) {
                    log.warn("Failed to decode Supabase JWT from session cookie for section {}: {}", sectionId,
                            ex.getMessage());
                }
            }
        }
        if (supabaseJwt == null) {
            String reason = "No Supabase JWT provided via principal, headers, or cookies";
            log.warn("Rejecting companion access token request for section {}: {}", sectionId, reason);
            return ResponseEntity.status(401)
                    .body(new ErrorResponse("error", reason));
        }
        Collection<? extends GrantedAuthority> grantedAuthorities =
                authentication != null ? authentication.getAuthorities() : List.of();
        IssuedToken issuedToken = tokenService.issueToken(
                profile.getId(),
                sectionId,
                supabaseJwt,
                grantedAuthorities);
        CompanionAccessTokenResponse response = new CompanionAccessTokenResponse(
                issuedToken.token(),
                issuedToken.expiresAt());
        return ResponseEntity.ok(response);
    }

    public record CompanionAccessTokenRequest(UUID sectionId) {
    }

    public record CompanionAccessTokenResponse(String token, OffsetDateTime expiresAt) {
    }

    public record ErrorResponse(String status, String message) {
    }
}
