package com.smartattendance.supabase.auth;

import org.springframework.http.HttpStatusCode;

public class SupabaseAuthException extends RuntimeException {

    private final HttpStatusCode status;

    public SupabaseAuthException(HttpStatusCode status, String message) {
        super(message != null && !message.isBlank() ? message : status.toString());
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
