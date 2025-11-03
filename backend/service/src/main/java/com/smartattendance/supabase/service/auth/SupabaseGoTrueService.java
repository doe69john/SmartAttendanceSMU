package com.smartattendance.supabase.service.auth;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.auth.SupabaseAuthException;
import com.smartattendance.supabase.auth.SupabaseGoTrueProperties;
import com.smartattendance.supabase.dto.auth.SupabaseAuthSession;
import com.smartattendance.supabase.repository.ProfileRepository;

import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@Service
public class SupabaseGoTrueService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseGoTrueService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final SupabaseGoTrueProperties properties;
    private final ProfileRepository profileRepository;

    public SupabaseGoTrueService(WebClient.Builder webClientBuilder,
            SupabaseGoTrueProperties properties,
            ProfileRepository profileRepository) {
        this.properties = properties;
        this.profileRepository = profileRepository;
        String baseUrl = Optional.ofNullable(properties.getBaseUrl()).filter(url -> !url.isBlank()).orElseThrow(
                () -> new IllegalStateException("supabase.gotrue.base-url must be configured"));
        this.webClient = webClientBuilder
                .clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Client-Info", "smartattendance-backend")
                .build();
    }

    public boolean userExistsByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        String normalizedEmail = StringUtils.trimWhitespace(email);
        return normalizedEmail != null && profileRepository.findByEmailIgnoreCase(normalizedEmail).isPresent();
    }

    public SupabaseAuthSession signIn(String email, String password) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);

        SupabaseAuthSession session = webClient.post()
                .uri("/token?grant_type=password")
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", requireAnonKey())
                .bodyValue(payload)
                .exchangeToMono(response -> handleResponse(response, SupabaseAuthSession.class))
                .blockOptional(Duration.ofSeconds(30))
                .orElseThrow(() -> new SupabaseAuthException(HttpStatusCode.valueOf(502),
                        "Empty response from Supabase sign-in"));

        assertProfileIsActive(session);

        return session;
    }

    public void signOut(String accessToken, String refreshToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (refreshToken != null && !refreshToken.isBlank()) {
            payload.put("refresh_token", refreshToken);
        }
        try {
            webClient.post()
                    .uri("/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("apikey", requireAnonKey())
                    .bodyValue(payload)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful() || response.statusCode().value() == 401) {
                            return response.releaseBody();
                        }
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty(response.statusCode().toString())
                                .map(body -> extractErrorMessage(body, response))
                                .flatMap(message -> Mono.error(new SupabaseAuthException(response.statusCode(), message)));
                    })
                    .block(Duration.ofSeconds(15));
        } catch (SupabaseAuthException ex) {
            if (ex.getStatus().value() == 401 || ex.getStatus().value() == 400) {
                log.debug("Supabase sign-out returned {}: {}", ex.getStatus(), ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    public void signUp(String email, String password, Map<String, Object> userData) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("data", userData);
        payload.put("gotrue_meta_security", Map.of());

        webClient.post()
                .uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", requireAnonKey())
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody();
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty(response.statusCode().toString())
                            .map(body -> extractErrorMessage(body, response))
                            .flatMap(message -> Mono.error(new SupabaseAuthException(response.statusCode(), message)));
                })
                .block(Duration.ofSeconds(30));
    }

    public void requestPasswordReset(String email) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", StringUtils.trimWhitespace(email));
        String redirectUrl = StringUtils.trimWhitespace(properties.getResetRedirectUrl());
        String anonKey = requireAnonKey();

        webClient.post()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/recover");
                    if (StringUtils.hasText(redirectUrl)) {
                        builder.queryParam("redirect_to", redirectUrl);
                    }
                    return builder.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", anonKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + anonKey)
                .bodyValue(payload)
                .exchangeToMono(response -> handleResponse(response, Void.class))
                .block(Duration.ofSeconds(30));
    }

    public void confirmPasswordReset(String accessToken, String newPassword) {
        Map<String, Object> payload = Map.of("password", newPassword);

        webClient.put()
                .uri("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("apikey", requireAnonKey())
                .bodyValue(payload)
                .exchangeToMono(response -> handleResponse(response, Void.class))
                .block(Duration.ofSeconds(30));
    }

    private <T> Mono<T> handleResponse(ClientResponse response, Class<T> bodyType) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(bodyType);
        }
        return response.bodyToMono(String.class)
                .defaultIfEmpty(response.statusCode().toString())
                .map(body -> extractErrorMessage(body, response))
                .flatMap(message -> Mono.error(new SupabaseAuthException(response.statusCode(), message)));
    }

    private String extractErrorMessage(String body, ClientResponse response) {
        if (body == null || body.isBlank()) {
            return response.statusCode().toString();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            if (node.hasNonNull("error_description")) {
                return node.get("error_description").asText();
            }
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (JsonProcessingException ex) {
            log.debug("Failed to parse Supabase error payload: {}", ex.getMessage());
        }
        return body;
    }

    private String requireAnonKey() {
        String anonKey = properties.getAnonKey();
        if (anonKey == null || anonKey.isBlank()) {
            throw new IllegalStateException("supabase.gotrue.anon-key must be configured");
        }
        return anonKey;
    }

    private void assertProfileIsActive(SupabaseAuthSession session) {
        if (session == null || session.getUser() == null || !StringUtils.hasText(session.getUser().getId())) {
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(session.getUser().getId());
        } catch (IllegalArgumentException ex) {
            log.warn("Supabase returned non-UUID user id: {}", session.getUser().getId());
            return;
        }

        profileRepository.findByUserId(userId).ifPresent(profile -> {
            if (Boolean.FALSE.equals(profile.getActive())) {
                try {
                    signOut(session.getAccessToken(), session.getRefreshToken());
                } catch (SupabaseAuthException ex) {
                    log.debug("Failed to revoke session for inactive user {}: {}", userId, ex.getMessage());
                }
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This account has been deactivated. Please contact your administrator.");
            }
        });
    }

}
