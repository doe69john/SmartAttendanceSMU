package com.smartattendance.supabase.web.support;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StudentOwnershipGuard {

    private final AuthenticationResolver authenticationResolver;
    private final boolean skipOwnershipChecks;

    public StudentOwnershipGuard(AuthenticationResolver authenticationResolver,
                                 @Value("${app.security.skip-ownership-checks:false}") boolean skipOwnershipChecks) {
        this.authenticationResolver = authenticationResolver;
        this.skipOwnershipChecks = skipOwnershipChecks;
    }

    public void requireOwnership(Authentication authentication,
                                 UUID studentId,
                                 Supplier<ResponseStatusException> missingStudentIdHandler,
                                 Supplier<ResponseStatusException> forbiddenHandler) {
        if (skipOwnershipChecks) {
            return;
        }

        if (studentId == null) {
            throw resolve(missingStudentIdHandler,
                    () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required"));
        }

        UUID callerId = authenticationResolver.resolveUserId(authentication).orElse(null);
        if (studentId.equals(callerId)) {
            return;
        }

        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(ga -> "ROLE_ADMIN".equals(ga.getAuthority())
                        || "ROLE_PROFESSOR".equals(ga.getAuthority()))) {
            return;
        }

        throw resolve(forbiddenHandler,
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this student"));
    }

    private static ResponseStatusException resolve(Supplier<ResponseStatusException> supplier,
                                                   Supplier<ResponseStatusException> fallback) {
        if (supplier != null) {
            ResponseStatusException exception = supplier.get();
            if (exception != null) {
                return exception;
            }
        }
        return fallback.get();
    }
}
