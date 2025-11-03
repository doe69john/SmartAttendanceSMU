package com.smartattendance.companion;

public final class CompanionHttpException extends RuntimeException {

    private final int statusCode;

    public CompanionHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public CompanionHttpException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
