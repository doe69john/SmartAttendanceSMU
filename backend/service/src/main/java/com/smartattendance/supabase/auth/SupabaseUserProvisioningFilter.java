package com.smartattendance.supabase.auth;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.smartattendance.security.Role;
import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.repository.ProfileRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SupabaseUserProvisioningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SupabaseUserProvisioningFilter.class);
    private static final String API_PREFIX = "/api/";

    private final ProfileRepository profileRepository;
    private final AtomicBoolean provisioningDisabled = new AtomicBoolean(false);

    public SupabaseUserProvisioningFilter(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (provisioningDisabled.get()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authentication instanceof JwtAuthenticationToken token && authentication.isAuthenticated()) {
            try {
                handleProvisioning(token);
            } catch (DataAccessException ex) {
                if (provisioningDisabled.compareAndSet(false, true)) {
                    log.warn("Disabling profile provisioning due to persistence error: {}", ex.getMessage());
                } else {
                    log.debug("Profile provisioning remains disabled: {}", ex.getMessage());
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void handleProvisioning(JwtAuthenticationToken token) {
        if (provisioningDisabled.get()) {
            return;
        }
        Jwt jwt = token.getToken();
        UUID userId = parseUuid(jwt.getSubject());
        if (userId == null) {
            return;
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = token.getName();
        }
        if (email == null || email.isBlank()) {
            log.debug("Skipping provisioning for subject {} due to missing email", userId);
            return;
        }
        String fullName = extractFullName(jwt);
        Role role = resolveRole(token.getAuthorities());
        provisionProfile(userId, email, fullName, role, jwt);
    }

    private void provisionProfile(UUID userId, String email, String fullName, Role appRole, Jwt jwt) {
        profileRepository.findByUserId(userId).ifPresentOrElse(profile -> {
            boolean changed = false;
            if (!email.equalsIgnoreCase(profile.getEmail())) {
                profile.setEmail(email);
                changed = true;
            }
            if (fullName != null && !fullName.isBlank() && !fullName.equals(profile.getFullName())) {
                profile.setFullName(fullName);
                changed = true;
            }
            ProfileEntity.Role desiredRole = mapProfileRole(appRole);
            if (profile.getRole() != desiredRole) {
                profile.setRole(desiredRole);
                changed = true;
            }
            if (changed) {
                profile.setUpdatedAt(OffsetDateTime.now());
                profileRepository.save(profile);
            }
        }, () -> {
            ProfileEntity profile = new ProfileEntity();
            profile.setId(UUID.randomUUID());
            profile.setUserId(userId);
            profile.setEmail(email);
            profile.setFullName(fullName != null && !fullName.isBlank() ? fullName : deriveNameFromEmail(email));
            profile.setRole(mapProfileRole(appRole));
            profile.setActive(Boolean.TRUE);
            profile.setCreatedAt(OffsetDateTime.now());
            profile.setUpdatedAt(OffsetDateTime.now());
            applyMetadata(profile, jwt);
            profileRepository.save(profile);
            log.info("Provisioned Supabase profile row for {}", email);
        });
    }

    private void applyMetadata(ProfileEntity profile, Jwt jwt) {
        Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
        if (userMetadata == null) {
            return;
        }
        Object studentId = userMetadata.get("student_id");
        if (studentId instanceof String id && !id.isBlank()) {
            profile.setStudentIdentifier(id);
        }
        Object staffId = userMetadata.get("staff_id");
        if (staffId instanceof String id && !id.isBlank()) {
            profile.setStaffId(id);
        }
        Object phone = userMetadata.get("phone");
        if (phone instanceof String value && !value.isBlank()) {
            profile.setPhone(value);
        }
        Object avatar = userMetadata.get("avatar_url");
        if (avatar instanceof String value && !value.isBlank()) {
            profile.setAvatarUrl(value);
        }
    }

    private Role resolveRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities.stream().anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()))) {
            return Role.ADMIN;
        }
        if (authorities.stream().anyMatch(auth -> "ROLE_PROFESSOR".equals(auth.getAuthority()))) {
            return Role.PROFESSOR;
        }
        return Role.STUDENT;
    }

    private ProfileEntity.Role mapProfileRole(Role role) {
        return switch (role) {
            case ADMIN -> ProfileEntity.Role.admin;
            case PROFESSOR -> ProfileEntity.Role.professor;
            default -> ProfileEntity.Role.student;
        };
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            log.debug("Subject is not a UUID: {}", value);
            return null;
        }
    }

    private String extractFullName(Jwt jwt) {
        String fullName = jwt.getClaimAsString("name");
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
        if (userMetadata != null) {
            Object value = userMetadata.get("full_name");
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
            value = userMetadata.get("name");
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    private String deriveNameFromEmail(String email) {
        int at = email.indexOf('@');
        if (at > 0) {
            return email.substring(0, at);
        }
        return email;
    }
}
