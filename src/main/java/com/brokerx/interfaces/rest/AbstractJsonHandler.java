package com.brokerx.interfaces.rest;

import com.brokerx.interfaces.rest.dto.ApiError;
import com.brokerx.interfaces.rest.dto.ApiResponse;
import com.brokerx.observability.AppMetrics;
import com.brokerx.observability.StructuredLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractJsonHandler implements HttpHandler {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String ALLOW_METHODS = "GET,POST,PUT,DELETE,PATCH,OPTIONS";
    private static final String ALLOW_HEADERS = "Authorization,Content-Type,Idempotency-Key";
    private static final String EXPOSE_HEADERS = "Location";

    private static final String STATUS_KEY = "__brokerx_status";

    private final TokenService tokenService;
    private final StructuredLogger logger;

    protected AbstractJsonHandler() {
        this(null);
    }

    protected AbstractJsonHandler(TokenService tokenService) {
        this.tokenService = tokenService;
        this.logger = StructuredLogger.get(getClass());
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        int statusSnapshot = HttpURLConnection.HTTP_INTERNAL_ERROR;
        try {
            applyCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                recordStatus(exchange, HttpURLConnection.HTTP_NO_CONTENT);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", JSON_CONTENT_TYPE);
            if (requiresAuthentication(exchange)) {
                TokenPrincipal principal = tokenService.require(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.setAttribute(TokenPrincipal.ATTRIBUTE, principal);
            }
            doHandle(exchange);
        } catch (RestException ex) {
            sendError(exchange, ex.status(), ex.error(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            sendError(exchange, HttpURLConnection.HTTP_BAD_REQUEST, null, ex.getMessage());
        } catch (JsonProcessingException ex) {
            sendError(exchange, HttpURLConnection.HTTP_BAD_REQUEST, null, "Invalid JSON payload: " + ex.getOriginalMessage());
        } catch (Exception ex) {
            sendError(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, null, ex.getMessage());
        } finally {
            statusSnapshot = resolveStatus(exchange, statusSnapshot);
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            AppMetrics.observeHttp(path, method, statusSnapshot, duration);
            Map<String, Object> fields = new HashMap<>();
            fields.put("path", path);
            fields.put("method", method);
            fields.put("status", statusSnapshot);
            fields.put("durationMs", duration.toMillis());
            logger.info("http_request", fields);
            exchange.close();
        }
    }

    protected boolean requiresAuthentication(HttpExchange exchange) {
        return tokenService != null;
    }

    protected TokenPrincipal principal(HttpExchange exchange) {
        return (TokenPrincipal) exchange.getAttribute(TokenPrincipal.ATTRIBUTE);
    }

    private void applyCors(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", ALLOW_METHODS);
        headers.set("Access-Control-Allow-Headers", ALLOW_HEADERS);
        headers.set("Access-Control-Expose-Headers", EXPOSE_HEADERS);
    }

    protected abstract void doHandle(HttpExchange exchange) throws IOException;

    protected <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            if (body == null) {
                throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Request body required");
            }
            byte[] raw = body.readAllBytes();
            if (raw.length == 0) {
                throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Request body required");
            }
            return JsonSupport.mapper().readValue(raw, type);
        }
    }

    protected void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] payload = JsonSupport.mapper().writeValueAsBytes(value);
        recordStatus(exchange, status);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    protected void sendData(HttpExchange exchange, int status, Object data) throws IOException {
        sendJson(exchange, status, new ApiResponse<>(data));
    }

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        recordStatus(exchange, HttpURLConnection.HTTP_NO_CONTENT);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
    }

    protected List<String> pathSegments(String path, String prefix) {
        String remainder = path.substring(prefix.length());
        if (remainder.isEmpty()) {
            return List.of();
        }
        return Stream.of(remainder.split("/"))
                .filter(segment -> segment != null && !segment.isBlank())
                .collect(Collectors.toList());
    }

    private void sendError(HttpExchange exchange, int status, String errorCode, String message) throws IOException {
        if (message == null || message.isBlank()) {
            message = HttpStatus.reason(status);
        }
        String errorName = errorCode != null ? errorCode : HttpStatus.reason(status);
        Map<String, Object> fields = new HashMap<>();
        fields.put("path", exchange.getRequestURI().getPath());
        fields.put("status", status);
        fields.put("error", errorName);
        if (message != null && !message.isBlank()) {
            fields.put("detail", message);
        }
        if (errorCode != null) {
            fields.put("code", errorCode);
        }
        if (status >= 500) {
            logger.error("http_error", fields);
        } else {
            logger.warn("http_error", fields);
        }
        ApiError error = new ApiError(
                Instant.now(),
                status,
                errorName,
                message,
                exchange.getRequestURI().getPath(),
                UUID.randomUUID().toString()
        );
        recordStatus(exchange, status);
        byte[] payload = JsonSupport.mapper().writeValueAsBytes(error);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private void recordStatus(HttpExchange exchange, int status) {
        exchange.setAttribute(STATUS_KEY, status);
    }

    private int resolveStatus(HttpExchange exchange, int fallback) {
        Object value = exchange.getAttribute(STATUS_KEY);
        if (value instanceof Integer integer) {
            return integer;
        }
        return fallback;
    }

    protected static final class HttpStatus {
        private HttpStatus() {
        }

        static String reason(int status) {
            return switch (status) {
                case HttpURLConnection.HTTP_OK -> "OK";
                case HttpURLConnection.HTTP_CREATED -> "Created";
                case HttpURLConnection.HTTP_NO_CONTENT -> "No Content";
                case HttpURLConnection.HTTP_BAD_REQUEST -> "Bad Request";
                case HttpURLConnection.HTTP_UNAUTHORIZED -> "Unauthorized";
                case HttpURLConnection.HTTP_FORBIDDEN -> "Forbidden";
                case HttpURLConnection.HTTP_NOT_FOUND -> "Not Found";
                case HttpURLConnection.HTTP_CONFLICT -> "Conflict";
                case HttpURLConnection.HTTP_INTERNAL_ERROR -> "Internal Server Error";
                default -> "HTTP " + status;
            };
        }
    }
}
