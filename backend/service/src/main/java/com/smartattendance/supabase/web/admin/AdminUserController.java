package com.smartattendance.supabase.web.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smartattendance.supabase.dto.admin.AdminUserDetailDto;
import com.smartattendance.supabase.dto.admin.AdminUserSummaryDto;
import com.smartattendance.supabase.dto.admin.AdminUserUpdateRequest;
import com.smartattendance.supabase.service.admin.AdminUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Users", description = "Administrative management of professor and student accounts")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Returns a filtered list of users for administrative oversight.")
    public List<AdminUserSummaryDto> listUsers(@RequestParam(name = "role", required = false) String role,
                                               @RequestParam(name = "q", required = false) String query,
                                               @RequestParam(name = "limit", required = false) Integer limit) {
        return adminUserService.listUsers(role, query, limit);
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Get user", description = "Fetches a specific user's profile for editing.")
    public AdminUserDetailDto getUser(@PathVariable("profileId") UUID profileId) {
        return adminUserService.getUser(profileId);
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Update user", description = "Updates profile details for a professor or student.")
    public AdminUserDetailDto updateUser(@PathVariable("profileId") UUID profileId,
                                         @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.updateUser(profileId, request);
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete user", description = "Permanently deletes a user and related data.")
    public ResponseEntity<Void> deleteUser(@PathVariable("profileId") UUID profileId) {
        adminUserService.deleteUser(profileId);
        return ResponseEntity.noContent().build();
    }
}
