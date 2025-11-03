package com.smartattendance.supabase.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase.auth")
public record SupabaseAuthProperties(
        String jwksUri,
        String issuer,
        String audience,
        String jwtSecret) {
}
