package com.brokerx.interfaces.rest.microservices;

import com.brokerx.application.AuthService;
import com.brokerx.application.StockService;
import com.brokerx.application.WalletService;
import com.brokerx.interfaces.rest.AbstractJsonHandler;
import com.brokerx.interfaces.rest.RestException;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.interfaces.rest.dto.AccountSummaryResponse;
import com.brokerx.interfaces.rest.dto.FollowedStockDto;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

public final class AccountSummaryHandler extends AbstractJsonHandler {
    private final AuthService authService;
    private final WalletService walletService;
    private final StockService stockService;

    public AccountSummaryHandler(AuthService authService,
                                 WalletService walletService,
                                 StockService stockService,
                                 TokenService tokenService) {
        super(tokenService);
        this.authService = authService;
        this.walletService = walletService;
        this.stockService = stockService;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        String contextPath = exchange.getHttpContext().getPath();
        String path = exchange.getRequestURI().getPath().substring(contextPath.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String[] segments = path.split("/");
        if (segments.length != 2 || !"summary".equals(segments[1])) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        UUID accountId = parseUuid(segments[0]);
        var account = authService.findAccount(accountId)
                .orElseThrow(() -> new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Compte introuvable"));
        BigDecimal balance = walletService.findWallet(accountId)
                .map(w -> w.getBalance().setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        List<FollowedStockDto> followed = stockService.listFollowed(accountId).stream()
                .map(quote -> new FollowedStockDto(
                        quote.id(),
                        quote.symbol(),
                        quote.name(),
                        quote.price(),
                        quote.updatedAt()
                ))
                .toList();
        AccountSummaryResponse response = new AccountSummaryResponse(
                account.getId(),
                account.getEmail(),
                account.getState(),
                balance,
                followed
        );
        sendData(exchange, HttpURLConnection.HTTP_OK, response);
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "accountId invalide");
        }
    }
}
