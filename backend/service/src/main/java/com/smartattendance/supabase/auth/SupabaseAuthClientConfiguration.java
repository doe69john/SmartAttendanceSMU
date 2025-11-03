package com.smartattendance.supabase.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SupabaseGoTrueProperties.class)
public class SupabaseAuthClientConfiguration {
}
