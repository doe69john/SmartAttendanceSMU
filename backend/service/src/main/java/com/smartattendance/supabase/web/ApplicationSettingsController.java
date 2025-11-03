package com.smartattendance.supabase.web;

import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartattendance.security.RequestRateLimiter;
import com.smartattendance.security.AdminPasscodeService;
import com.smartattendance.security.ProfessorPasscodeService;
import com.smartattendance.supabase.dto.ApplicationSettingsResponse;
import com.smartattendance.supabase.dto.PasscodeValidationRequest;
import com.smartattendance.supabase.dto.PasscodeValidationResponse;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/settings")
@Validated
@Tag(name = "Application Settings", description = "Manage configuration for passcode-protected operations")
public class ApplicationSettingsController {

    private final ProfessorPasscodeService professorPasscodeService;
    private final AdminPasscodeService adminPasscodeService;
    private final RequestRateLimiter rateLimiter;

    public ApplicationSettingsController(ProfessorPasscodeService professorPasscodeService,
                                         AdminPasscodeService adminPasscodeService,
                                         RequestRateLimiter rateLimiter) {
        this.professorPasscodeService = professorPasscodeService;
        this.adminPasscodeService = adminPasscodeService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    @Operation(summary = "Fetch application settings", description = "Returns passcode requirements for staff tools.")
    public ApplicationSettingsResponse getSettings() {
        return new ApplicationSettingsResponse(
            professorPasscodeService.isPasscodeRequired(),
            adminPasscodeService.isPasscodeRequired());
    }

    @PostMapping("/validate-staff-passcode")
    @Operation(summary = "Validate staff passcode", description = "Verifies whether the submitted staff passcode matches the configured value.")
    public ResponseEntity<PasscodeValidationResponse> validatePasscode(@Valid @RequestBody(required = false) PasscodeValidationRequest request,
                                                                       HttpServletRequest httpRequest) {
        rateLimiter.assertAllowed("settings:validate-passcode", httpRequest, Duration.ofMinutes(1), 10);
        String candidate = request != null ? sanitizeCandidate(request.getPasscode()) : null;
        boolean valid = professorPasscodeService.validatePasscode(candidate);
        return ResponseEntity.ok(new PasscodeValidationResponse(valid));
    }

    @PostMapping("/validate-admin-passcode")
    @Operation(summary = "Validate admin passcode", description = "Verifies whether the submitted admin passcode matches the configured value.")
    public ResponseEntity<PasscodeValidationResponse> validateAdminPasscode(@Valid @RequestBody(required = false) PasscodeValidationRequest request,
                                                                            HttpServletRequest httpRequest) {
        rateLimiter.assertAllowed("settings:validate-admin-passcode", httpRequest, Duration.ofMinutes(1), 10);
        String candidate = request != null ? sanitizeCandidate(request.getPasscode()) : null;
        boolean valid = adminPasscodeService.validatePasscode(candidate);
        return ResponseEntity.ok(new PasscodeValidationResponse(valid));
    }

    private static String sanitizeCandidate(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
