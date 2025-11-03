package com.smartattendance.supabase.web;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.auth.AccountAlreadyExistsException;
import com.smartattendance.supabase.auth.SupabaseAuthException;
import com.smartattendance.security.RequestRateLimiter;
import com.smartattendance.supabase.auth.SupabaseSessionCookieService;
import com.smartattendance.supabase.service.auth.RegistrationValidationService;
import com.smartattendance.supabase.service.auth.SupabaseGoTrueService;
import com.smartattendance.supabase.dto.auth.AuthSessionResponse;
import com.smartattendance.supabase.dto.auth.PasswordResetConfirmRequest;
import com.smartattendance.supabase.dto.auth.PasswordResetRequest;
import com.smartattendance.supabase.dto.auth.SignInRequest;
import com.smartattendance.supabase.dto.auth.SignUpRequest;
import com.smartattendance.supabase.dto.auth.SupabaseAuthSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "Supabase-backed authentication flow")
public class SupabaseAuthController {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthController.class);

    private final SupabaseGoTrueService goTrueService;
    private final SupabaseSessionCookieService cookieService;
    private final RegistrationValidationService registrationValidationService;
    private final RequestRateLimiter rateLimiter;

    public SupabaseAuthController(SupabaseGoTrueService goTrueService,
                                  SupabaseSessionCookieService cookieService,
                                  RegistrationValidationService registrationValidationService,
                                  RequestRateLimiter rateLimiter) {
        this.goTrueService = goTrueService;
        this.cookieService = cookieService;
        this.registrationValidationService = registrationValidationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/sign-in")
    @Operation(summary = "Sign in", description = "Authenticates a user with email and password via Supabase.")
    public ResponseEntity<AuthSessionResponse> signIn(@Valid @RequestBody SignInRequest request,
                                                      HttpServletRequest httpRequest,
                                                      HttpServletResponse response) {
        rateLimiter.assertAllowed("auth:sign-in", httpRequest, Duration.ofMinutes(1), 5);
        SupabaseAuthSession session = goTrueService.signIn(request.getEmail(), request.getPassword());
        cookieService.storeSession(response, session);
        AuthSessionResponse payload = new AuthSessionResponse(session);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/sign-out")
    @Operation(summary = "Sign out", description = "Clears Supabase session cookies and revokes refresh tokens when possible.")
    public ResponseEntity<Void> signOut(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = cookieService.readAccessToken(request).orElse(null);
        String refreshToken = cookieService.readRefreshToken(request).orElse(null);
        if (accessToken != null) {
            try {
                goTrueService.signOut(accessToken, refreshToken);
            } catch (SupabaseAuthException ex) {
                log.debug("Supabase sign-out failed: {}", ex.getMessage());
            }
        }
        cookieService.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Sign up", description = "Creates a new Supabase user account after validating registration policies.")
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequest request,
                                       HttpServletRequest httpRequest) {
        rateLimiter.assertAllowed("auth:sign-up", httpRequest, Duration.ofMinutes(5), 3);
        registrationValidationService.ensureRegistrationAllowed(request.getEmail(), request.getUserData());
        try {
            goTrueService.signUp(request.getEmail(), request.getPassword(), request.getUserData());
        } catch (SupabaseAuthException ex) {
            if (ex.getStatus().value() == 422) {
                throw new AccountAlreadyExistsException("An account with this email already exists. Please sign in instead.");
            }
            throw ex;
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset")
    @Operation(summary = "Request password reset", description = "Requests a Supabase password recovery email for the supplied account.")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                     HttpServletRequest httpRequest) {
        rateLimiter.assertAllowed("auth:password-reset", httpRequest, Duration.ofMinutes(5), 3);
        goTrueService.requestPasswordReset(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Confirm password reset", description = "Updates a user's password using a Supabase recovery access token.")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse response) {
        rateLimiter.assertAllowed("auth:password-reset-confirm", httpRequest, Duration.ofMinutes(1), 5);
        goTrueService.confirmPasswordReset(request.getAccessToken(), request.getPassword());
        cookieService.clearSession(response);
        return ResponseEntity.noContent().build();
    }
}
