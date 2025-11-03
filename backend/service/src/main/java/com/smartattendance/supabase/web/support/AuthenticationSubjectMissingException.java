package com.smartattendance.supabase.web.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown when the current authentication does not expose a valid UUID subject.
 */
public class AuthenticationSubjectMissingException extends ResponseStatusException {

    public AuthenticationSubjectMissingException() {
        super(HttpStatus.UNAUTHORIZED, "Authentication token is missing a valid subject");
    }
}
