package com.smartattendance.supabase.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthSessionResponse {

    private SessionDto session;

    public AuthSessionResponse() {
    }

    public AuthSessionResponse(SupabaseAuthSession session) {
        if (session != null) {
            this.session = new SessionDto();
            this.session.setAccessToken(session.getAccessToken());
            this.session.setTokenType(session.getTokenType());
            this.session.setExpiresIn(session.getExpiresIn());
            this.session.setExpiresAt(session.getExpiresAt());
            this.session.setUser(session.getUser());
        }
    }

    public SessionDto getSession() {
        return session;
    }

    public void setSession(SessionDto session) {
        this.session = session;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SessionDto {
        private String accessToken;
        private Long expiresIn;
        private Long expiresAt;
        private String tokenType;
        private SupabaseUserDto user;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public Long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public SupabaseUserDto getUser() {
            return user;
        }

        public void setUser(SupabaseUserDto user) {
            this.user = user;
        }
    }
}
