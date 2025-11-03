package com.smartattendance.supabase.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.ProfessorDashboardSummary;
import com.smartattendance.supabase.dto.StudentDashboardSummary;
import com.smartattendance.supabase.service.dashboard.DashboardService;
import com.smartattendance.supabase.web.support.AuthenticationResolver;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Summary metrics for professors and students")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthenticationResolver authenticationResolver;

    public DashboardController(DashboardService dashboardService, AuthenticationResolver authenticationResolver) {
        this.dashboardService = dashboardService;
        this.authenticationResolver = authenticationResolver;
    }

    @GetMapping("/professor")
    @PreAuthorize("hasRole('PROFESSOR') or hasRole('ADMIN')")
    @Operation(summary = "Professor dashboard", description = "Provides aggregate counts for the authenticated professor.")
    public ProfessorDashboardSummary professor(Authentication authentication) {
        return dashboardService.loadProfessorSummary(authenticationResolver.resolveUserId(authentication).orElse(null));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT') or hasRole('PROFESSOR') or hasRole('ADMIN')")
    @Operation(summary = "Student dashboard", description = "Provides attendance insights for the authenticated student.")
    public StudentDashboardSummary student(Authentication authentication) {
        return dashboardService.loadStudentSummary(authenticationResolver.resolveUserId(authentication).orElse(null));
    }
}
