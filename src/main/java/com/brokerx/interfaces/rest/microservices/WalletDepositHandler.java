package com.brokerx.interfaces.rest.microservices;

import com.brokerx.application.WalletService;
import com.brokerx.domain.wallet.Transaction;
import com.brokerx.interfaces.rest.AbstractJsonHandler;
import com.brokerx.interfaces.rest.RestException;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.interfaces.rest.dto.DepositRequest;
import com.brokerx.interfaces.rest.dto.DepositResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.util.UUID;

public final class WalletDepositHandler extends AbstractJsonHandler {
    private final WalletService walletService;

    public WalletDepositHandler(WalletService walletService, TokenService tokenService) {
        super(tokenService);
        this.walletService = walletService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        String contextPath = exchange.getHttpContext().getPath();
        String path = exchange.getRequestURI().getPath().substring(contextPath.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String[] segments = path.split("/");
        if (segments.length != 2 || !"deposits".equals(segments[1])) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        UUID accountId = parseUuid(segments[0]);
        DepositRequest request = readJson(exchange, DepositRequest.class);
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "idempotencyKey requis");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "amount doit etre positif");
        }
        Transaction tx = walletService.deposit(accountId, request.idempotencyKey(), request.amount().doubleValue());
        BigDecimal balance = walletService.findWallet(accountId)
                .map(wallet -> wallet.getBalance().setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        DepositResponse response = new DepositResponse(
                tx.getId(),
                tx.getAmount(),
                balance
        );
        sendData(exchange, HttpURLConnection.HTTP_CREATED, response);
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "accountId invalide");
        }
    }
}
