package com.smartattendance.companion;

public record HealthResponse(String status, String version, boolean sessionActive, String message) {
}
