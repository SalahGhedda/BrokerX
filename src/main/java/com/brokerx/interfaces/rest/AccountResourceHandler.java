package com.brokerx.interfaces.rest;

import com.brokerx.application.AuthService;
import com.brokerx.application.NotificationService;
import com.brokerx.application.OrderService;
import com.brokerx.application.OrderService.OrderResult;
import com.brokerx.application.StockService;
import com.brokerx.application.StockService.Quote;
import com.brokerx.application.WalletService;
import com.brokerx.domain.account.AccountState;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.interfaces.rest.dto.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

final class AccountResourceHandler extends AbstractJsonHandler {
    private static final String BASE_PATH = "/api/v1/accounts/";

    private final AuthService authService;
    private final WalletService walletService;
    private final StockService stockService;
    private final OrderService orderService;
    private final NotificationService notificationService;

    AccountResourceHandler(AuthService authService,
                           WalletService walletService,
                           StockService stockService,
                           OrderService orderService,
                           NotificationService notificationService,
                           TokenService tokenService) {
        super(tokenService);
        this.authService = authService;
        this.walletService = walletService;
        this.stockService = stockService;
        this.orderService = orderService;
        this.notificationService = notificationService;
    }

    @Override
    protected boolean requiresAuthentication(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(BASE_PATH)) {
            return false;
        }
        List<String> segments = pathSegments(path, BASE_PATH);
        if (segments.size() >= 2 && "confirmations".equals(segments.get(1))) {
            return false;
        }
        return true;
    }

    @Override
    protected void doHandle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(BASE_PATH)) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        List<String> segments = pathSegments(path, BASE_PATH);
        if (segments.isEmpty()) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Account id missing");
        }

        UUID accountId = parseUuid(segments.get(0), "accountId invalide");
        List<String> remaining = segments.subList(1, segments.size());
        String method = exchange.getRequestMethod().toUpperCase();

        if (remaining.isEmpty()) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }

        UserAccount account = authService.findAccount(accountId)
                .orElseThrow(() -> new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Compte introuvable"));

        TokenPrincipal principal = principal(exchange);
        boolean confirmationRoute = isConfirmationRoute(remaining);
        if (!confirmationRoute) {
            ensureAuthenticated(principal);
            ensureOwner(principal, accountId);
        }

        switch (remaining.get(0)) {
            case "confirmations" -> handleConfirmation(exchange, method, account);
            case "summary" -> handleSummary(exchange, method, account);
            case "wallet" -> handleWallet(exchange, method, account, remaining);
            case "stocks" -> handleStocks(exchange, method, account, remaining);
            case "orders" -> handleOrders(exchange, method, account, remaining);
            case "notifications" -> handleNotifications(exchange, method, account, remaining);
            default -> throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
    }

    private void handleConfirmation(HttpExchange exchange, String method, UserAccount account) throws IOException {
        if (!"POST".equals(method)) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        ConfirmAccountRequest request = readJson(exchange, ConfirmAccountRequest.class);
        if (request == null || request.verificationCode() == null || request.verificationCode().isBlank()) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "verificationCode requis");
        }
        authService.confirmAccount(account.getId(), request.verificationCode());
        sendNoContent(exchange);
    }

    private void handleSummary(HttpExchange exchange, String method, UserAccount account) throws IOException {
        if (!"GET".equals(method)) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        BigDecimal balance = walletService.findWallet(account.getId())
                .map(w -> w.getBalance().setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        List<FollowedStockDto> followed = stockService.listFollowed(account.getId()).stream()
                .map(this::toFollowedStock)
                .collect(Collectors.toList());
        AccountSummaryResponse response = new AccountSummaryResponse(
                account.getId(),
                account.getEmail(),
                account.getState(),
                balance,
                followed
        );
        sendData(exchange, HttpURLConnection.HTTP_OK, response);
    }

    private void handleWallet(HttpExchange exchange, String method, UserAccount account, List<String> segments) throws IOException {
        if (segments.size() != 2 || !"wallet".equals(segments.get(0)) || !"deposits".equals(segments.get(1))) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        if (!"POST".equals(method)) {
            throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
        }
        ensureActive(account);
        DepositRequest request = readJson(exchange, DepositRequest.class);
        if (request == null || request.amount() == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Montant requis");
        }
        if (request.amount().signum() <= 0) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, "Montant doit etre > 0");
        }
        String idempotencyKey = Optional.ofNullable(request.idempotencyKey())
                .filter(key -> !key.isBlank())
                .orElse("API-" + System.nanoTime());

        var tx = walletService.deposit(account.getId(), idempotencyKey, request.amount().doubleValue());
        BigDecimal balance = walletService.findWallet(account.getId())
                .map(w -> w.getBalance().setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        DepositResponse response = new DepositResponse(
                tx.getId(),
                tx.getAmount(),
                balance
        );
        sendData(exchange, HttpURLConnection.HTTP_CREATED, response);
    }

    private void handleStocks(HttpExchange exchange, String method, UserAccount account, List<String> segments) throws IOException {
        ensureActive(account);
        if (segments.size() == 2 && "followed".equals(segments.get(1))) {
            if ("GET".equals(method)) {
                List<FollowedStockDto> followed = stockService.listFollowed(account.getId()).stream()
                        .map(this::toFollowedStock)
                        .toList();
                sendData(exchange, HttpURLConnection.HTTP_OK, followed);
                return;
            }
            if ("POST".equals(method)) {
                FollowRequest request = readJson(exchange, FollowRequest.class);
                UUID stockId = parseUuid(request.stockId(), "stockId invalide");
                stockService.follow(account.getId(), stockId);
                sendNoContent(exchange);
                return;
            }
        }
        if (segments.size() == 3 && "followed".equals(segments.get(1)) && "DELETE".equals(method)) {
            UUID stockId = parseUuid(segments.get(2), "stockId invalide");
            stockService.unfollow(account.getId(), stockId);
            sendNoContent(exchange);
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
    }

    private void handleOrders(HttpExchange exchange, String method, UserAccount account, List<String> segments) throws IOException {
        ensureActive(account);
        if (segments.size() == 1) {
            if ("GET".equals(method)) {
                List<OrderResponse> orders = orderService.listOrders(account.getId()).stream()
                        .map(this::toOrderResponse)
                        .toList();
                sendData(exchange, HttpURLConnection.HTTP_OK, new OrdersResponse(orders));
                return;
            }
            if ("POST".equals(method)) {
                OrderRequest request = readJson(exchange, OrderRequest.class);
                OrderResult result = orderService.placeOrder(account.getId(), toCommand(request));
                OrderResponse response = toOrderResponse(result);
                exchange.getResponseHeaders().add("Location",
                        "/api/v1/accounts/" + account.getId() + "/orders/" + response.orderId());
                sendData(exchange, HttpURLConnection.HTTP_CREATED, response);
                return;
            }
        }
        if (segments.size() == 3 && "cancel".equals(segments.get(2)) && "POST".equals(method)) {
            UUID orderId = parseUuid(segments.get(1), "orderId invalide");
            OrderResult cancelled = orderService.cancelOrder(account.getId(), orderId);
            sendData(exchange, HttpURLConnection.HTTP_OK, toOrderResponse(cancelled));
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
    }

    private void handleNotifications(HttpExchange exchange, String method, UserAccount account, List<String> segments) throws IOException {
        if (segments.size() != 1) {
            throw new RestException(HttpURLConnection.HTTP_NOT_FOUND, "Route not found");
        }
        if ("GET".equals(method)) {
            List<NotificationResponse> notifications = notificationService.list(account.getId()).stream()
                    .map(this::toNotification)
                    .toList();
            sendData(exchange, HttpURLConnection.HTTP_OK, new NotificationsResponse(notifications));
            return;
        }
        if ("DELETE".equals(method)) {
            notificationService.clear(account.getId());
            sendNoContent(exchange);
            return;
        }
        throw new RestException(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
    }

    private FollowedStockDto toFollowedStock(Quote quote) {
        return new FollowedStockDto(
                quote.id(),
                quote.symbol(),
                quote.name(),
                quote.price(),
                quote.updatedAt()
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

    private NotificationResponse toNotification(NotificationService.Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.category(),
                notification.message(),
                notification.referenceId(),
                notification.createdAt(),
                notification.payload()
        );
    }

    private OrderService.OrderCommand toCommand(OrderRequest request) {
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
        return new OrderService.OrderCommand(
                request.symbol(),
                request.side(),
                request.type(),
                String.valueOf(request.quantity()),
                request.limitPrice() != null ? request.limitPrice().toPlainString() : null,
                request.clientOrderId()
        );
    }

    private boolean isConfirmationRoute(List<String> segments) {
        return segments.size() == 1 && "confirmations".equals(segments.get(0));
    }

    private void ensureAuthenticated(TokenPrincipal principal) {
        if (principal == null) {
            throw new RestException(HttpURLConnection.HTTP_UNAUTHORIZED, "Token requis");
        }
    }

    private void ensureOwner(TokenPrincipal principal, UUID accountId) {
        if (!principal.accountId().equals(accountId)) {
            throw new RestException(HttpURLConnection.HTTP_FORBIDDEN, "Compte different");
        }
    }

    private void ensureActive(UserAccount account) {
        if (account.getState() != AccountState.ACTIVE) {
            throw new RestException(HttpURLConnection.HTTP_CONFLICT, "Compte inactif");
        }
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

    private UUID parseUuid(UUID value, String message) {
        if (value == null) {
            throw new RestException(HttpURLConnection.HTTP_BAD_REQUEST, message);
        }
        return value;
    }
}

