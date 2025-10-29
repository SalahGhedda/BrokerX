package com.brokerx.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.application.AuthService;
import com.brokerx.application.NotificationService;
import com.brokerx.application.OrderService;
import com.brokerx.application.OrderService.OrderCommand;
import com.brokerx.application.OrderService.OrderResult;
import com.brokerx.application.StockService;
import com.brokerx.application.WalletService;
import com.brokerx.interfaces.rest.ApiRouter;
import com.brokerx.interfaces.rest.MetricsHandler;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.domain.wallet.Transaction;
import com.brokerx.domain.wallet.Wallet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class UiHttpServer implements AutoCloseable {
    private static final String JSON_CONTENT = "application/json; charset=utf-8";

    private final HttpServer server;
    private final AuthService authService;
    private final WalletService walletService;
    private final StockService stockService;
    private final OrderService orderService;
    private final TokenService tokenService;
    private final NotificationService notificationService;

    public UiHttpServer(int port,
                        AuthService authService,
                        WalletService walletService,
                        StockService stockService,
                        OrderService orderService,
                        TokenService tokenService,
                        NotificationService notificationService) {
        this.authService = authService;
        this.walletService = walletService;
        this.stockService = stockService;
        this.orderService = orderService;
        this.tokenService = tokenService;
        this.notificationService = notificationService;
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start HTTP server", e);
        }
        registerRoutes();
    }

    private void registerRoutes() {
        server.createContext("/", new StaticFileHandler("public/index.html", "text/html; charset=utf-8"));
        server.createContext("/confirm.html", new StaticFileHandler("public/confirm.html", "text/html; charset=utf-8"));
        server.createContext("/dashboard.html", new StaticFileHandler("public/dashboard.html", "text/html; charset=utf-8"));
        server.createContext("/stocks.html", new StaticFileHandler("public/stocks.html", "text/html; charset=utf-8"));
        server.createContext("/stock.html", new StaticFileHandler("public/stock.html", "text/html; charset=utf-8"));
        server.createContext("/assets/styles.css", new StaticFileHandler("public/styles.css", "text/css; charset=utf-8"));
        server.createContext("/assets/app.js", new StaticFileHandler("public/app.js", "application/javascript; charset=utf-8"));
        server.createContext("/api/auth/signup", this::handleSignup);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/accounts/confirm", this::handleAccountConfirmation);
        server.createContext("/api/accounts/summary", this::handleAccountSummary);
        server.createContext("/api/wallets/deposit", this::handleDeposit);
        server.createContext("/api/stocks", this::handleStocksList);
        server.createContext("/api/stocks/follow", this::handleFollowStock);
        server.createContext("/api/stocks/unfollow", this::handleUnfollowStock);
        server.createContext("/api/stocks/followed", this::handleFollowedStocks);
        server.createContext("/api/stocks/details", this::handleStockDetails);
        server.createContext("/api/orders", this::handleOrders);
        ApiRouter.register(server, authService, walletService, stockService, orderService, tokenService, notificationService);
        server.createContext("/api/orders/cancel", this::handleOrderCancel);
        server.createContext("/metrics", new MetricsHandler());
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleSignup(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String email = trimToNull(data.get("email"));
        String phone = trimToNull(data.get("phone"));
        String password = trimToNull(data.get("password"));
        String fullName = trimToNull(data.get("fullName"));
        String address = trimToNull(data.get("addressLine"));
        String dobRaw = trimToNull(data.get("dateOfBirth"));
        if (email == null || password == null || fullName == null || phone == null || address == null || dobRaw == null) {
            sendError(exchange, 400, "Champs requis manquants");
            return;
        }
        java.time.LocalDate dateOfBirth;
        try {
            dateOfBirth = java.time.LocalDate.parse(dobRaw);
        } catch (java.time.format.DateTimeParseException ex) {
            sendError(exchange, 400, "Format de date invalide (YYYY-MM-DD)");
            return;
        }
        try {
            var result = authService.register(new AuthService.SignupCommand(
                    email,
                    phone,
                    password,
                    fullName,
                    address,
                    dateOfBirth
            ));
            String body = """
                {
                  "status":"SUCCESS",
                  "accountId":"%s",
                  "email":"%s",
                  "verificationCode":"%s",
                  "expiresAt":"%s"
                }
                """.formatted(result.accountId(), escapeJson(result.email()), result.verificationCode(), result.expiresAt());
            sendJson(exchange, 201, body);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String email = trimToNull(data.get("email"));
        String password = trimToNull(data.get("password"));
        if (email == null || password == null) {
            sendError(exchange, 400, "Email and password are required");
            return;
        }
        Optional<UserAccount> account = authService.authenticate(email, password);
        if (account.isEmpty()) {
            sendError(exchange, 401, "Invalid credentials or inactive account");
            return;
        }
        var acc = account.get();
        String body = """
            {
              "status":"SUCCESS",
              "accountId":"%s",
              "email":"%s",
              "state":"%s"
            }
            """.formatted(acc.getId(), escapeJson(acc.getEmail()), acc.getState().name());
        sendJson(exchange, 200, body);
    }

    private void handleAccountSummary(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String accountIdRaw = query.get("accountId");
        if (accountIdRaw == null || accountIdRaw.isBlank()) {
            sendError(exchange, 400, "Account ID is required");
            return;
        }
        try {
            UUID accountId = UUID.fromString(accountIdRaw.trim());
            Optional<UserAccount> accountOpt = authService.findAccount(accountId);
            if (accountOpt.isEmpty()) {
                sendError(exchange, 404, "Account not found");
                return;
            }
            Optional<Wallet> walletOpt = walletService.findWallet(accountId);
            var balance = walletOpt.map(w -> w.getBalance().toPlainString()).orElse("0.00");
            var followed = stockService.listFollowed(accountId);

            var account = accountOpt.get();
            String body = """
                {
                  "accountId":"%s",
                  "email":"%s",
                  "state":"%s",
                  "balance":"%s",
                  "followedStocks":%s
                }
                """.formatted(account.getId(), escapeJson(account.getEmail()), account.getState().name(), balance, quotesToJson(followed));
            sendJson(exchange, 200, body);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, "Invalid account ID format");
        }
    }

    private void handleAccountConfirmation(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String accountIdRaw = trimToNull(data.get("accountId"));
        String code = trimToNull(data.get("verificationCode"));
        if (accountIdRaw == null || code == null) {
            sendError(exchange, 400, "accountId et verificationCode requis");
            return;
        }
        try {
            UUID accountId = UUID.fromString(accountIdRaw);
            authService.confirmAccount(accountId, code);
            sendJson(exchange, 200, """
                {
                  "status":"SUCCESS",
                  "accountId":"%s"
                }
                """.formatted(accountId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleDeposit(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String accountIdRaw = data.get("accountId");
        String amountRaw = data.get("amount");
        if (accountIdRaw == null || amountRaw == null) {
            sendError(exchange, 400, "Account ID and amount are required");
            return;
        }
        try {
            UUID accountId = UUID.fromString(accountIdRaw.trim());
            double amount = Double.parseDouble(amountRaw);
            String idem = Optional.ofNullable(data.get("idempotencyKey"))
                    .filter(key -> !key.isBlank())
                    .orElseGet(() -> "UI-" + System.nanoTime());

            Transaction tx = walletService.deposit(accountId, idem, amount);
            String balance = walletService.findWallet(accountId)
                    .map(w -> w.getBalance().toPlainString())
                    .orElse("0.00");

            String body = """
                {
                  "status":"SUCCESS",
                  "transactionId":"%s",
                  "amount":"%s",
                  "balance":"%s"
                }
                """.formatted(tx.getId(), tx.getAmount().toPlainString(), balance);
            sendJson(exchange, 200, body);
        } catch (NumberFormatException ex) {
            sendError(exchange, 400, "Invalid amount format");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, ex.getMessage());
        } catch (RuntimeException ex) {
            sendError(exchange, 500, "Unexpected error: " + ex.getMessage());
        }
    }

    private void handleStocksList(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        var quotes = stockService.listAll();
        sendJson(exchange, 200, quotesToJson(quotes));
    }

    private void handleFollowStock(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String accountRaw = trimToNull(data.get("accountId"));
        String stockRaw = trimToNull(data.get("stockId"));
        if (accountRaw == null || stockRaw == null) {
            sendError(exchange, 400, "accountId et stockId requis");
            return;
        }
        try {
            stockService.follow(UUID.fromString(accountRaw), UUID.fromString(stockRaw));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleUnfollowStock(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String accountRaw = trimToNull(data.get("accountId"));
        String stockRaw = trimToNull(data.get("stockId"));
        if (accountRaw == null || stockRaw == null) {
            sendError(exchange, 400, "accountId et stockId requis");
            return;
        }
        try {
            stockService.unfollow(UUID.fromString(accountRaw), UUID.fromString(stockRaw));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleFollowedStocks(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String accountRaw = trimToNull(query.get("accountId"));
        if (accountRaw == null) {
            sendError(exchange, 400, "accountId requis");
            return;
        }
        try {
            var quotes = stockService.listFollowed(UUID.fromString(accountRaw));
            sendJson(exchange, 200, quotesToJson(quotes));
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleStockDetails(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String stockRaw = trimToNull(query.get("stockId"));
        if (stockRaw == null) {
            sendError(exchange, 400, "stockId requis");
            return;
        }
        try {
            var quote = stockService.getQuote(UUID.fromString(stockRaw));
            sendJson(exchange, 200, quoteToJson(quote));
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private String quotesToJson(List<StockService.Quote> quotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < quotes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(quoteToJson(quotes.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String quoteToJson(StockService.Quote quote) {
        return "{"
            + "\"id\":\"" + quote.id() + "\","
            + "\"symbol\":\"" + quote.symbol() + "\","
            + "\"name\":\"" + escapeJson(quote.name()) + "\","
            + "\"description\":\"" + escapeJson(quote.description()) + "\","
            + "\"price\":" + quote.price().toPlainString() + ","
            + "\"updatedAt\":\"" + quote.updatedAt() + "\""
            + "}";
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        if ("GET".equals(method)) {
            handleOrderList(exchange);
            return;
        }
        if (!"POST".equals(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String accountIdRaw = trimToNull(data.get("accountId"));
        if (accountIdRaw == null) {
            sendError(exchange, 400, "accountId is required");
            return;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdRaw);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, "Invalid account ID format");
            return;
        }
        OrderCommand command = new OrderCommand(
                data.get("symbol"),
                Optional.ofNullable(data.get("side")).orElse("BUY"),
                Optional.ofNullable(data.get("type")).orElse("MARKET"),
                data.get("quantity"),
                data.get("price"),
                data.get("clientOrderId")
        );
        try {
            OrderResult result = orderService.placeOrder(accountId, command);
            String body = toOrderJson(result);
            sendJson(exchange, 200, body);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleOrderCancel(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, String> data = parseFormData(exchange);
        String accountIdRaw = trimToNull(data.get("accountId"));
        String orderIdRaw = trimToNull(data.get("orderId"));
        if (accountIdRaw == null || orderIdRaw == null) {
            sendError(exchange, 400, "accountId and orderId are required");
            return;
        }
        UUID accountId;
        UUID orderId;
        try {
            accountId = UUID.fromString(accountIdRaw);
            orderId = UUID.fromString(orderIdRaw);
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, "Invalid UUID format");
            return;
        }
        try {
            OrderResult result = orderService.cancelOrder(accountId, orderId);
            sendJson(exchange, 200, toOrderJson(result));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(exchange, 400, ex.getMessage());
        }
    }

    private void handleOrderList(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
        String accountIdRaw = trimToNull(query.get("accountId"));
        if (accountIdRaw == null) {
            sendError(exchange, 400, "accountId is required");
            return;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdRaw);
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, "Invalid account ID format");
            return;
        }
        List<OrderResult> orders = orderService.listOrders(accountId);
        StringBuilder body = new StringBuilder();
        body.append("[");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append(toOrderJson(orders.get(i)));
        }
        body.append("]");
        sendJson(exchange, 200, body.toString());
    }

    private String toOrderJson(OrderResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("{")
                .append("\"orderId\":\"").append(result.orderId()).append("\",")
                .append("\"stockId\":\"").append(result.stockId()).append("\",")
                .append("\"symbol\":\"").append(escapeJson(result.symbol())).append("\",")
                .append("\"side\":\"").append(result.side()).append("\",")
                .append("\"type\":\"").append(result.type()).append("\",")
                .append("\"quantity\":").append(result.quantity()).append(",");

        if (result.limitPrice() != null) {
            builder.append("\"limitPrice\":").append(result.limitPrice().toPlainString()).append(",");
        } else {
            builder.append("\"limitPrice\":null,");
        }
        if (result.executedPrice() != null) {
            builder.append("\"executedPrice\":").append(result.executedPrice().toPlainString()).append(",");
        } else {
            builder.append("\"executedPrice\":null,");
        }
        if (result.notional() != null) {
            builder.append("\"notional\":").append(result.notional().toPlainString()).append(",");
        } else {
            builder.append("\"notional\":null,");
        }
        builder.append("\"status\":\"").append(result.status()).append("\",")
                .append("\"createdAt\":\"").append(result.createdAt()).append("\",");
        if (result.executedAt() != null) {
            builder.append("\"executedAt\":\"").append(result.executedAt()).append("\",");
        } else {
            builder.append("\"executedAt\":null,");
        }
        if (result.failureReason() != null) {
            builder.append("\"failureReason\":\"").append(escapeJson(result.failureReason())).append("\"");
        } else {
            builder.append("\"failureReason\":null");
        }
        builder.append("}");
        return builder.toString();
    }

    private Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return parseQuery(body);
        }
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> data = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return data;
        }
        for (String pair : raw.split("&")) {
            var parts = pair.split("=", 2);
            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                data.put(key, value);
            }
        }
        return data;
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String body = """
            {
              "status":"ERROR",
              "message":"%s"
            }
            """.formatted(escapeJson(message));
        sendJson(exchange, status, body);
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "Method not allowed");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class StaticFileHandler implements HttpHandler {
        private final String resourcePath;
        private final String contentType;

        private StaticFileHandler(String resourcePath, String contentType) {
            this.resourcePath = resourcePath;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            var classLoader = Thread.currentThread().getContextClassLoader();
            try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
                if (input == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = input.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }
}


