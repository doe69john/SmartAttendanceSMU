package com.smartattendance.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.smartattendance.supabase.auth.SupabaseSessionCookieFilter;
import com.smartattendance.supabase.auth.SupabaseUserProvisioningFilter;
import com.smartattendance.supabase.service.companion.CompanionAccessTokenService;
import com.smartattendance.supabase.web.companion.CompanionTokenFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final SupabaseUserProvisioningFilter userProvisioningFilter;
    private final SupabaseSessionCookieFilter sessionCookieFilter;
    private final CompanionTokenFilter companionTokenFilter;
    private final List<String> allowedOrigins;

    public SecurityConfiguration(Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
                                 SupabaseUserProvisioningFilter userProvisioningFilter,
                                 SupabaseSessionCookieFilter sessionCookieFilter,
                                 CompanionAccessTokenService companionAccessTokenService,
                                 @Value("${app.security.allowed-origins:}") String allowedOriginsProperty) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.userProvisioningFilter = userProvisioningFilter;
        this.sessionCookieFilter = sessionCookieFilter;
        this.companionTokenFilter = new CompanionTokenFilter(companionAccessTokenService);
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicApiEndpoints.API_PATTERNS).permitAll()
                        .requestMatchers("/api/companion/assets/**", "/api/companion/sections/**").permitAll()
                        .requestMatchers("/api/companion/releases/latest").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterBefore(companionTokenFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(sessionCookieFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(userProvisioningFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(resolveAllowedOrigins());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private List<String> resolveAllowedOrigins() {
        if (!allowedOrigins.isEmpty()) {
            return allowedOrigins;
        }
        return List.of(
                "http://localhost:*",
                "https://localhost:*",
                "http://127.0.0.1:*",
                "https://127.0.0.1:*"
        );
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher nonApiMatcher = new NegatedRequestMatcher(new AntPathRequestMatcher("/api/**"));

        http.securityMatcher(nonApiMatcher)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
