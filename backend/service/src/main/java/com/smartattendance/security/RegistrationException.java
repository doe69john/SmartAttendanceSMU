package com.smartattendance.security;

/**
 * Thrown when a self-service registration attempt fails validation.
 */
public class RegistrationException extends RuntimeException {

    public RegistrationException(String message) {
        super(message);
    }
}
