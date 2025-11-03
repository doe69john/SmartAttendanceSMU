package com.smartattendance.companion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompanionHttpServer {

    private static final String TOKEN_HEADER = "X-Companion-Token";
    private static final int BACKLOG = 0;

    private static final Logger logger = LoggerFactory.getLogger(CompanionHttpServer.class);

    private final CompanionSettings settings;
    private final CompanionSessionManager sessionManager;
    private final HttpServer server;
    private final ObjectMapper objectMapper;

    public CompanionHttpServer(CompanionSettings settings, CompanionSessionManager sessionManager) throws IOException {
        this.settings = settings;
        this.sessionManager = sessionManager;
        this.objectMapper = new ObjectMapper();
        this.server = HttpServer.create(new InetSocketAddress(settings.host(), settings.port()), BACKLOG);
        configureRoutes();
        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void configureRoutes() {
        server.createContext("/health", exchange -> handle(exchange, this::handleHealth));
        server.createContext("/handshake", exchange -> handle(exchange, this::handleHandshake));
        server.createContext("/session/start", exchange -> handle(exchange, this::handleSessionStart));
        server.createContext("/session/status", exchange -> handle(exchange, this::handleSessionStatus));
        server.createContext("/session/stop", exchange -> handle(exchange, this::handleSessionStop));
        server.createContext("/session/events", exchange -> handle(exchange, this::handleSessionEvents));
        server.createContext("/application/shutdown", exchange -> handle(exchange, this::handleShutdown));
    }

    private void handle(HttpExchange exchange, HttpHandlerFunction handler) throws IOException {
        try {
            applyCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            handler.handle(exchange);
        } catch (CompanionHttpException ex) {
            logger.warn("Companion request failed: path={}, method={}, status={}, token={}, message={}",
                    exchange.getRequestURI(), exchange.getRequestMethod(), ex.statusCode(),
                    CompanionSessionManager.maskToken(exchange.getRequestHeaders().getFirst(TOKEN_HEADER)),
                    ex.getMessage());
            writeJson(exchange, ex.statusCode(), Map.of(
                    "status", "error",
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            logger.error("Unexpected companion error: path={}, method={}, token={}, message={}",
                    exchange.getRequestURI(), exchange.getRequestMethod(),
                    CompanionSessionManager.maskToken(exchange.getRequestHeaders().getFirst(TOKEN_HEADER)),
                    ex.getMessage(), ex);
            writeJson(exchange, 500, Map.of(
                    "status", "error",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Unexpected companion error"
            ));
        } finally {
            exchange.close();
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        HealthResponse response = sessionManager.health();
        writeJson(exchange, 200, response);
    }

    private void handleHandshake(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        HandshakeRequest request = readJson(exchange, HandshakeRequest.class);
        HandshakeResponse response = sessionManager.performHandshake(request);
        writeJson(exchange, 200, response);
    }

    private void handleSessionStart(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        String token = requireToken(exchange.getRequestHeaders());
        StartSessionRequest request = readJson(exchange, StartSessionRequest.class);
        logger.info("Received /session/start: sessionId={}, sectionId={}, token={}",
                request.sessionId(), request.sectionId(), CompanionSessionManager.maskToken(token));
        StartSessionResponse response = sessionManager.startSession(token, request);
        writeJson(exchange, 200, response);
        logger.info("Responded /session/start: sessionId={}, status=200, payload={}",
                request.sessionId(), summarizeStartSessionResponse(response));
    }

    private Map<String, Object> summarizeStartSessionResponse(StartSessionResponse response) {
        return Map.of(
                "status", response.status(),
                "sessionId", response.sessionId(),
                "sectionId", response.sectionId(),
                "modelFile", fileName(response.modelPath()),
                "cascadeFile", fileName(response.cascadePath()),
                "labelsFile", fileName(response.labelsPath()),
                "downloadedBytes", response.downloadedBytes()
        );
    }

    private String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            return Path.of(path).getFileName().toString();
        } catch (Exception ignored) {
            return path;
        }
    }

    private void handleSessionStatus(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        String token = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        SessionStatusResponse response = sessionManager.sessionStatus(token);
        writeJson(exchange, 200, response);
    }

    private void handleSessionStop(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        String token = requireToken(exchange.getRequestHeaders());
        StopSessionResponse response = sessionManager.stopSession(token);
        writeJson(exchange, 200, response);
    }

    private void handleSessionEvents(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        String token = requireToken(exchange.getRequestHeaders());
        sessionManager.verifyToken(token);
        SessionRuntime runtime = sessionManager.activeSession();
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        if (runtime == null) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(": no active session\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            return;
        }
        var eventBus = runtime.eventBus();
        OutputStream os = exchange.getResponseBody();
        CountDownLatch latch = new CountDownLatch(1);
        var listener = new java.util.function.Consumer<com.smartattendance.companion.recognition.RecognitionEvent>() {
            @Override
            public void accept(com.smartattendance.companion.recognition.RecognitionEvent event) {
                try {
                    byte[] payload = objectMapper.writeValueAsBytes(mapEvent(event));
                    synchronized (os) {
                        os.write("event: recognition\n".getBytes(StandardCharsets.UTF_8));
                        os.write("data: ".getBytes(StandardCharsets.UTF_8));
                        os.write(payload);
                        os.write("\n\n".getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                } catch (IOException ex) {
                    latch.countDown();
                }
            }
        };
        eventBus.subscribe(listener);
        try {
            for (var event : eventBus.snapshot()) {
                listener.accept(event);
            }
            latch.await(15, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            eventBus.unsubscribe(listener);
            try {
                os.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleShutdown(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        String token = requireToken(exchange.getRequestHeaders());
        sessionManager.verifyToken(token);
        writeJson(exchange, 200, Map.of("status", "shutting-down"));
        new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            stop();
            sessionManager.shutdown();
            System.exit(0);
        }).start();
    }

    private void applyCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, " + TOKEN_HEADER);
        headers.set("Access-Control-Max-Age", "3600");
    }

    private String requireToken(Headers headers) {
        String token = headers.getFirst(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            throw new CompanionHttpException(401, "Companion handshake token missing");
        }
        return token.trim();
    }

    private void requireMethod(HttpExchange exchange, String method) {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new CompanionHttpException(405, method + " required");
        }
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            if (inputStream == null) {
                throw new CompanionHttpException(400, "Request body required");
            }
            return objectMapper.readValue(inputStream, type);
        } catch (CompanionHttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CompanionHttpException(400, "Invalid JSON payload: " + ex.getMessage(), ex);
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private Map<String, Object> mapEvent(com.smartattendance.companion.recognition.RecognitionEvent event) {
        return Map.of(
                "type", event.getType().name().toLowerCase(),
                "timestamp", event.getTimestamp().toString(),
                "trackId", event.getTrackId(),
                "studentId", event.getStudentId(),
                "studentName", event.getStudentName(),
                "confidence", event.getConfidence(),
                "message", event.getMessage(),
                "success", event.isSuccess(),
                "manual", event.isManual()
        );
    }

    @FunctionalInterface
    private interface HttpHandlerFunction {
        void handle(HttpExchange exchange) throws Exception;
    }
}
