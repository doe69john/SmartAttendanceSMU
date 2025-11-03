package com.smartattendance.supabase.auth;

import org.springframework.http.HttpStatus;

public class AccountAlreadyExistsException extends RuntimeException {

    private final HttpStatus status;

    public AccountAlreadyExistsException(String message) {
        super(message);
        this.status = HttpStatus.CONFLICT;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
