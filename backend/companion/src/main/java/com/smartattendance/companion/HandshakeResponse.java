package com.smartattendance.companion;

public record HandshakeResponse(String status, String token, String version, String message) {
}
