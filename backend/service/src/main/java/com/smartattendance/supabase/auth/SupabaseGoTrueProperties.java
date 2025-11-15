package com.smartattendance.supabase.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase.gotrue")
public class SupabaseGoTrueProperties {

    private String baseUrl;
    private String anonKey;
    private boolean cookieSecure = false;
    private String cookieDomain;
    private String cookieSameSite = "Lax";
    private String cookiePath = "/";
    private int refreshTokenTtlDays = 30;
    private String resetRedirectUrl;
    private String emailRedirectUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAnonKey() {
        return anonKey;
    }

    public void setAnonKey(String anonKey) {
        this.anonKey = anonKey;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public int getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(int refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String getResetRedirectUrl() {
        return resetRedirectUrl;
    }

    public void setResetRedirectUrl(String resetRedirectUrl) {
        this.resetRedirectUrl = resetRedirectUrl;
    }

    public String getEmailRedirectUrl() {
        return emailRedirectUrl;
    }

    public void setEmailRedirectUrl(String emailRedirectUrl) {
        this.emailRedirectUrl = emailRedirectUrl;
    }

}
