package com.smartattendance.supabase.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.CurrentUserResponse;
import com.smartattendance.supabase.service.profile.ProfileService;
import com.smartattendance.supabase.web.support.AuthenticationResolver;

@RestController
@RequestMapping("/api")
@Tag(name = "Profile", description = "Authenticated user profile lookup")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthenticationResolver authenticationResolver;

    public ProfileController(ProfileService profileService, AuthenticationResolver authenticationResolver) {
        this.profileService = profileService;
        this.authenticationResolver = authenticationResolver;
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Returns the authenticated user's profile and granted roles.")
    public ResponseEntity<CurrentUserResponse> currentUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return ResponseEntity.status(401).build();
        }
        return authenticationResolver.resolveUserId(authentication)
                .map(userId -> profileService.findByUserId(userId)
                        .map(profile -> {
                            CurrentUserResponse response = new CurrentUserResponse();
                            response.setProfile(profile);
                            response.setRoles(token.getAuthorities().stream()
                                    .map(authority -> authority.getAuthority())
                                    .toList());
                            return ResponseEntity.ok(response);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
