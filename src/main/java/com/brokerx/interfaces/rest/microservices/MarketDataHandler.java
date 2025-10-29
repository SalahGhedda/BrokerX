package com.brokerx.interfaces.rest.microservices;

import com.brokerx.application.StockService;
import com.brokerx.application.StockService.Quote;
import com.brokerx.interfaces.rest.AbstractJsonHandler;
import com.brokerx.interfaces.rest.RestException;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.interfaces.rest.dto.FollowedStockDto;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

public final class MarketDataHandler extends AbstractJsonHandler {
    private final StockService stockService;

    public MarketDataHandler(StockService stockService, TokenService tokenService) {
        super(tokenService);
        this.stockService = stockService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String contextPath = exchange.getHttpContext().getPath();
        String path = exchange.getRequestURI().getPath().substring(contextPath.length());
        if (path.isEmpty() || "/".equals(path)) {
            if (!"GET".equals(method)) {
                throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
            }
            handleList(exchange);
            return;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String[] segments = path.split("/");
        if (segments.length == 1 && "GET".equals(method)) {
            handleDetail(exchange, parseUuid(segments[0]));
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
    }

    private void handleList(HttpExchange exchange) throws IOException {
        List<FollowedStockDto> quotes = stockService.listAll().stream()
                .map(this::toDto)
                .toList();
        sendData(exchange, HttpURLConnection.HTTP_OK, quotes);
    }

    private void handleDetail(HttpExchange exchange, UUID stockId) throws IOException {
        Quote quote = stockService.getQuote(stockId);
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

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Identifiant invalide");
        }
    }
}
