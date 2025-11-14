package com.smartattendance.supabase.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BackendBaseUrlResolverTest {

    private final BackendBaseUrlResolver resolver = new BackendBaseUrlResolver();

    @Test
    void resolvesHttpsPortWhenForwardedProtoIsHttpsButPortIs80() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "smart.example.com:80");
        request.addHeader("X-Forwarded-Port", "80");

        assertThat(resolver.resolve(request)).isEqualTo("https://smart.example.com/api");
    }

    @Test
    void resolvesHttpsPortWhenHostHeaderIncludesPort80WithoutForwardedPort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "smart.example.com:80");

        assertThat(resolver.resolve(request)).isEqualTo("https://smart.example.com/api");
    }

    @Test
    void resolvesHttpPortWhenForwardedPortIs443() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "http");
        request.addHeader("X-Forwarded-Host", "smart.example.com:443");
        request.addHeader("X-Forwarded-Port", "443");

        assertThat(resolver.resolve(request)).isEqualTo("http://smart.example.com/api");
    }
}
