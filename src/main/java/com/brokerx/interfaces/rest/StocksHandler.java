package com.brokerx.interfaces.rest;

import com.brokerx.application.StockService;
import com.brokerx.application.StockService.Quote;
import com.brokerx.interfaces.rest.dto.FollowedStockDto;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

final class StocksHandler extends AbstractJsonHandler {
    private static final String BASE_PATH = "/api/v1/stocks";

    private final StockService stockService;

    StocksHandler(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();
        if (!path.startsWith(BASE_PATH)) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        List<String> segments = pathSegments(path, BASE_PATH);
        if (segments.isEmpty()) {
            handleList(exchange, method);
            return;
        }
        handleDetail(exchange, method, segments.get(0));
    }

    private void handleList(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        List<FollowedStockDto> quotes = stockService.listAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        sendData(exchange, HttpURLConnection.HTTP_OK, quotes);
    }

    private void handleDetail(HttpExchange exchange, String method, String stockIdRaw) throws IOException {
        if (!"GET".equals(method)) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        UUID stockId = parseUuid(stockIdRaw);
        Quote quote;
        try {
            quote = stockService.getQuote(stockId);
        } catch (IllegalArgumentException ex) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Titre introuvable");
        }
        sendData(exchange, HttpURLConnection.HTTP_OK, toDto(quote));
    }

    private FollowedStockDto toDto(Quote quote) {
        return new FollowedStockDto(
                quote.id(),
                quote.symbol(),
                quote.name(),
                quote.price(),
                quote.updatedAt()
        );
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Identifiant de titre invalide");
        }
    }
}
