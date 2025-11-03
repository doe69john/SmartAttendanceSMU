package com.smartattendance.supabase.service.admin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.dto.admin.AdminUserDetailDto;
import com.smartattendance.supabase.dto.admin.AdminUserSummaryDto;
import com.smartattendance.supabase.dto.admin.AdminUserUpdateRequest;
import com.smartattendance.supabase.repository.AdminUserJdbcRepository;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final int DEFAULT_LIMIT = 100;

    private final AdminUserJdbcRepository adminUserJdbcRepository;

    public AdminUserService(AdminUserJdbcRepository adminUserJdbcRepository) {
        this.adminUserJdbcRepository = adminUserJdbcRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryDto> listUsers(String role, String query, Integer limit) {
        String normalizedRole = normalizeRole(role);
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        int resolvedLimit = limit != null && limit > 0 ? Math.min(limit, 500) : DEFAULT_LIMIT;
        return adminUserJdbcRepository.listUsers(normalizedRole, normalizedQuery, resolvedLimit);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailDto getUser(UUID profileId) {
        return adminUserJdbcRepository.findUser(profileId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User profile not found"));
    }

    @Transactional
    public AdminUserDetailDto updateUser(UUID profileId, AdminUserUpdateRequest request) {
        AdminUserDetailDto existing = getUser(profileId);
        try {
            adminUserJdbcRepository.updateProfile(profileId, request);
            String nextEmail = request.getEmail();
            String currentEmail = existing.getEmail();
            if (existing.getUserId() != null && nextEmail != null) {
                boolean changed = currentEmail == null || !currentEmail.equalsIgnoreCase(nextEmail);
                if (changed) {
                    adminUserJdbcRepository.updateAuthUserEmail(existing.getUserId(), nextEmail);
                }
            }
        } catch (DataIntegrityViolationException ex) {
            log.warn("Failed to update user {} due to data integrity violation: {}", profileId, ex.getMessage());
            throw new ResponseStatusException(CONFLICT, "Email or identifier already in use", ex);
        }
        return adminUserJdbcRepository.findUser(profileId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User profile not found after update"));
    }

    @Transactional
    public void deleteUser(UUID profileId) {
        AdminUserDetailDto existing = getUser(profileId);
        if ("professor".equalsIgnoreCase(existing.getRole())) {
            if (adminUserJdbcRepository.professorHasSessions(profileId)) {
                throw new ResponseStatusException(CONFLICT,
                        "Professor has scheduled sessions. Reassign or delete sessions before deleting the user.");
            }
            if (adminUserJdbcRepository.professorHasSectionAssignments(profileId)) {
                throw new ResponseStatusException(CONFLICT,
                        "Professor is assigned to sections. Reassign sections before deleting the user.");
            }
        }
        try {
            adminUserJdbcRepository.deleteUser(profileId, existing.getUserId());
        } catch (DataAccessException ex) {
            log.error("Failed to delete user {}: {}", profileId, ex.getMessage(), ex);
            throw new ResponseStatusException(BAD_REQUEST, "Unable to delete user; see server logs for details", ex);
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String lowered = role.trim().toLowerCase(Locale.ROOT);
        return switch (lowered) {
            case "professor", "professors" -> "professor";
            case "student", "students" -> "student";
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported role filter: " + role);
        };
    }
}
