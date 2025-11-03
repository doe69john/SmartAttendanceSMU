package com.smartattendance.supabase.service.auth;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.smartattendance.supabase.auth.AccountAlreadyExistsException;
import com.smartattendance.supabase.repository.ProfileRepository;

@Service
public class RegistrationValidationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationValidationService.class);

    private final SupabaseGoTrueService goTrueService;
    private final ProfileRepository profileRepository;

    public RegistrationValidationService(SupabaseGoTrueService goTrueService, ProfileRepository profileRepository) {
        this.goTrueService = goTrueService;
        this.profileRepository = profileRepository;
    }

    public void ensureRegistrationAllowed(String email, Map<String, Object> userData) {
        if (StringUtils.hasText(email) && emailExists(email)) {
            throw new AccountAlreadyExistsException("An account with this email already exists. Please sign in instead.");
        }

        String studentId = extractString(userData, "student_id");
        if (StringUtils.hasText(studentId) && profileRepository.findByStudentIdentifier(studentId).isPresent()) {
            log.debug("Duplicate student ID rejected: {}", studentId);
            throw new AccountAlreadyExistsException("A student with this ID already exists. Please sign in instead.");
        }

        String staffId = extractString(userData, "staff_id");
        if (StringUtils.hasText(staffId) && profileRepository.findByStaffId(staffId).isPresent()) {
            log.debug("Duplicate staff ID rejected: {}", staffId);
            throw new AccountAlreadyExistsException("A staff member with this ID already exists. Please sign in instead.");
        }
    }

    private String extractString(Map<String, Object> userData, String key) {
        if (userData == null || userData.isEmpty()) {
            return null;
        }
        Object value = userData.get(key);
        return value instanceof String str ? str : null;
    }

    private boolean emailExists(String email) {
        return goTrueService.userExistsByEmail(email);
    }
}
