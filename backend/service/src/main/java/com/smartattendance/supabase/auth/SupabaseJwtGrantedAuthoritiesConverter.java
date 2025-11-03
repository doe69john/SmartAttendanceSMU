package com.smartattendance.supabase.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class SupabaseJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        addRole(roles, jwt.getClaimAsString("role"));

        Map<String, Object> appMetadata = jwt.getClaim("app_metadata");
        if (appMetadata != null) {
            addRole(roles, appMetadata.get("role"));
            addRoles(roles, appMetadata.get("roles"));
        }

        Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
        if (userMetadata != null) {
            addRole(roles, userMetadata.get("role"));
            addRoles(roles, userMetadata.get("roles"));
        }

        if (roles.isEmpty()) {
            return List.of();
        }

        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (String value : roles) {
            if (value != null && !value.isBlank()) {
                authorities.add(toAuthority(value));
            }
        }

        return authorities;
    }

    private void addRoles(Set<String> roles, Object source) {
        if (source instanceof Collection<?> collection) {
            for (Object value : collection) {
                addRole(roles, value);
            }
        } else {
            addRole(roles, source);
        }
    }

    private void addRole(Set<String> roles, Object value) {
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
    }

    private GrantedAuthority toAuthority(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return new SimpleGrantedAuthority(normalized);
    }
}
