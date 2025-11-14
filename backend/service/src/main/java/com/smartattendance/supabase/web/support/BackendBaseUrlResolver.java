package com.smartattendance.supabase.web.support;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class BackendBaseUrlResolver {

    private static final String API_SUFFIX = "/api";

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String scheme = firstNonBlank(singleHeader(request, "X-Forwarded-Proto"), request.getScheme(), "http");
        String hostHeader = firstNonBlank(singleHeader(request, "X-Forwarded-Host"), request.getServerName());
        String portHeader = singleHeader(request, "X-Forwarded-Port");
        HostAndPort hostAndPort = extractHostAndPort(hostHeader);
        int port = determinePort(portHeader, hostAndPort.port(), request.getServerPort(), scheme);
        int normalizedPort = normalizePortForScheme(scheme, port);
        String prefix = combinePaths(request.getContextPath(), singleHeader(request, "X-Forwarded-Prefix"), API_SUFFIX);
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme(scheme)
                .host(hostAndPort.host());
        if (normalizedPort > 0 && normalizedPort != defaultPortForScheme(scheme)) {
            builder.port(normalizedPort);
        }
        builder.path(prefix);
        String uri = builder.build().toUriString();
        return uri.endsWith("/") && uri.length() > 1 ? uri.substring(0, uri.length() - 1) : uri;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        String header = request.getHeader(name);
        if (header == null) {
            return null;
        }
        int commaIndex = header.indexOf(',');
        String value = commaIndex >= 0 ? header.substring(0, commaIndex) : header;
        return value != null ? value.trim() : null;
    }

    private static HostAndPort extractHostAndPort(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return new HostAndPort("localhost", -1);
        }
        int colonIndex = hostHeader.indexOf(':');
        if (colonIndex > 0 && colonIndex < hostHeader.length() - 1) {
            String host = hostHeader.substring(0, colonIndex);
            String portPart = hostHeader.substring(colonIndex + 1);
            try {
                return new HostAndPort(host, Integer.parseInt(portPart));
            } catch (NumberFormatException ignored) {
                return new HostAndPort(host, -1);
            }
        }
        return new HostAndPort(hostHeader, -1);
    }

    private static int determinePort(String forwardedPort,
                                     int hostHeaderPort,
                                     int serverPort,
                                     String scheme) {
        if (forwardedPort != null && !forwardedPort.isBlank()) {
            try {
                return Integer.parseInt(forwardedPort.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (hostHeaderPort > 0) {
            return hostHeaderPort;
        }
        if (serverPort > 0) {
            return serverPort;
        }
        return defaultPortForScheme(scheme);
    }

    private static int normalizePortForScheme(String scheme, int port) {
        int defaultPort = defaultPortForScheme(scheme);
        if (port <= 0) {
            return defaultPort;
        }
        if ("https".equalsIgnoreCase(scheme) && port == 80) {
            return defaultPort;
        }
        if ("http".equalsIgnoreCase(scheme) && port == 443) {
            return defaultPort;
        }
        return port;
    }

    private static int defaultPortForScheme(String scheme) {
        if (scheme == null) {
            return 80;
        }
        return scheme.equalsIgnoreCase("https") ? 443 : 80;
    }

    private static String combinePaths(String... segments) {
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            String sanitized = segment.trim();
            if (!sanitized.startsWith("/")) {
                sanitized = "/" + sanitized;
            }
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '/' && sanitized.startsWith("/")) {
                builder.append(sanitized.substring(1));
            } else {
                builder.append(sanitized);
            }
        }
        if (builder.length() == 0) {
            return "/";
        }
        return builder.toString();
    }

    private record HostAndPort(String host, int port) {}
}
