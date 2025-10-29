package com.brokerx.interfaces.rest.microservices;

import com.brokerx.application.OrderService;
import com.brokerx.application.OrderService.OrderCommand;
import com.brokerx.application.OrderService.OrderResult;
import com.brokerx.interfaces.rest.AbstractJsonHandler;
import com.brokerx.interfaces.rest.RestException;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.interfaces.rest.dto.OrderRequest;
import com.brokerx.interfaces.rest.dto.OrderResponse;
import com.brokerx.interfaces.rest.dto.OrdersResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrdersHandler extends AbstractJsonHandler {
    private static final String BASE_PATH = "/orders";

    private final OrderService orderService;

    public OrdersHandler(OrderService orderService, TokenService tokenService) {
        super(tokenService);
        this.orderService = orderService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(BASE_PATH)) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        String remainder = path.substring(BASE_PATH.length());
        if (remainder.isEmpty() || "/".equals(remainder)) {
            if ("GET".equals(method)) {
                handleList(exchange);
                return;
            }
            if ("POST".equals(method)) {
                handleCreate(exchange);
                return;
            }
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }

        String[] segments = remainder.startsWith("/") ? remainder.substring(1).split("/") : remainder.split("/");
        if (segments.length == 2 && "cancel".equals(segments[1]) && "POST".equals(method)) {
            handleCancel(exchange, parseUuid(segments[0], "orderId invalide"));
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
    }

    private void handleList(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
        UUID accountId = parseUuid(query.get("accountId"), "accountId requis");
        List<OrderResponse> orders = orderService.listOrders(accountId).stream()
                .map(this::toOrderResponse)
                .toList();
        sendData(exchange, HttpURLConnection.HTTP_OK, new OrdersResponse(orders));
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
        UUID accountId = parseUuid(query.get("accountId"), "accountId requis");
        OrderRequest request = readJson(exchange, OrderRequest.class);
        OrderResult result = orderService.placeOrder(accountId, toCommand(request));
        sendData(exchange, HttpURLConnection.HTTP_CREATED, toOrderResponse(result));
    }

    private void handleCancel(HttpExchange exchange, UUID orderId) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
        UUID accountId = parseUuid(query.get("accountId"), "accountId requis");
        OrderResult cancelled = orderService.cancelOrder(accountId, orderId);
        sendData(exchange, HttpURLConnection.HTTP_OK, toOrderResponse(cancelled));
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> data = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return data;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                data.put(urlDecode(parts[0]), urlDecode(parts[1]));
            }
        }
        return data;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private UUID parseUuid(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, message);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, message);
        }
    }

    private OrderCommand toCommand(OrderRequest request) {
        if (request == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Payload requis");
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Symbole requis");
        }
        if (request.side() == null || request.side().isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Side requis");
        }
        if (request.type() == null || request.type().isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Type requis");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Quantite doit etre positive");
        }
        if ("LIMIT".equalsIgnoreCase(request.type()) && request.limitPrice() == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "limitPrice requis pour une limite");
        }
        return new OrderCommand(
                request.symbol(),
                request.side(),
                request.type(),
                String.valueOf(request.quantity()),
                request.limitPrice() != null ? request.limitPrice().toPlainString() : null,
                request.clientOrderId()
        );
    }

    private OrderResponse toOrderResponse(OrderResult result) {
        return new OrderResponse(
                result.orderId(),
                result.stockId(),
                result.symbol(),
                result.type(),
                result.side(),
                result.quantity(),
                result.limitPrice(),
                result.executedPrice(),
                result.notional(),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.executedAt(),
                result.failureReason()
        );
    }
}
