package com.smartattendance.supabase.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@EnableConfigurationProperties(SupabaseAuthProperties.class)
public class SupabaseJwtConfiguration {

    @Bean
    public JwtDecoder supabaseJwtDecoder(SupabaseAuthProperties properties) {
        List<JwtDecoder> delegates = new ArrayList<>();

        if (properties.jwksUri() != null && !properties.jwksUri().isBlank()) {
            NimbusJwtDecoder jwkDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
            applyValidators(jwkDecoder, properties);
            delegates.add(jwkDecoder);
        }

        if (properties.jwtSecret() != null && !properties.jwtSecret().isBlank()) {
            String secret = properties.jwtSecret();
            delegates.add(buildAndValidateSecretDecoder(secret.getBytes(StandardCharsets.UTF_8), properties));

            byte[] decoded = tryBase64(secret);
            if (decoded != null) {
                delegates.add(buildAndValidateSecretDecoder(decoded, properties));
            }
        }

        if (delegates.isEmpty()) {
            throw new IllegalStateException("supabase.auth.jwks-uri or supabase.auth.jwt-secret must be configured");
        }

        if (delegates.size() == 1) {
            return delegates.get(0);
        }

        return token -> {
            JwtException last = null;
            for (JwtDecoder delegate : delegates) {
                try {
                    return delegate.decode(token);
                } catch (JwtException ex) {
                    last = ex;
                }
            }
            throw last != null ? last : new JwtException("Failed to decode Supabase JWT");
        };
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> supabaseJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new SupabaseJwtGrantedAuthoritiesConverter());
        return converter;
    }

    private JwtDecoder buildAndValidateSecretDecoder(byte[] secretBytes, SupabaseAuthProperties properties) {
        NimbusJwtDecoder decoder = buildSecretKeyDecoder(secretBytes);
        applyValidators(decoder, properties);
        return decoder;
    }

    private NimbusJwtDecoder buildSecretKeyDecoder(byte[] secretBytes) {
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private byte[] tryBase64(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void applyValidators(NimbusJwtDecoder decoder, SupabaseAuthProperties properties) {
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(properties.issuer());
        if (properties.audience() != null && !properties.audience().isBlank()) {
            validator = new DelegatingOAuth2TokenValidator<>(validator, new SupabaseAudienceValidator(properties.audience()));
        }
        decoder.setJwtValidator(validator);
    }
}
