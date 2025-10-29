package com.brokerx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.application.MarketDataService.MarketDataSnapshot;
import com.brokerx.domain.account.AccountState;
import com.brokerx.domain.order.OrderAuditEntry;
import com.brokerx.domain.order.OrderSide;
import com.brokerx.domain.order.OrderStatus;
import com.brokerx.domain.order.OrderType;
import com.brokerx.domain.order.TradeOrder;
import com.brokerx.domain.position.Position;
import com.brokerx.domain.stock.Stock;
import com.brokerx.observability.AppMetrics;
import com.brokerx.observability.StructuredLogger;
import com.brokerx.ports.OrderAuditRepository;
import com.brokerx.ports.OrderRepository;
import com.brokerx.ports.PositionRepository;
import com.brokerx.ports.StockRepository;
import com.brokerx.ports.TransactionManager;

public class OrderService {
    private final AuthService authService;
    private final WalletService walletService;
    private final MarketDataService marketDataService;
    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final PositionRepository positionRepository;
    private final OrderAuditRepository orderAuditRepository;
    private final NotificationService notificationService;
    private final TransactionManager transactionManager;
    private final StructuredLogger logger;

    public OrderService(
            AuthService authService,
            WalletService walletService,
            MarketDataService marketDataService,
            OrderRepository orderRepository,
            StockRepository stockRepository,
            PositionRepository positionRepository,
            OrderAuditRepository orderAuditRepository,
            NotificationService notificationService,
            TransactionManager transactionManager
    ) {
        this.authService = authService;
        this.walletService = walletService;
        this.marketDataService = marketDataService;
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.positionRepository = positionRepository;
        this.orderAuditRepository = orderAuditRepository;
        this.notificationService = notificationService;
        this.transactionManager = transactionManager;
        this.logger = StructuredLogger.get(OrderService.class);
    }

    public OrderResult placeOrder(UUID accountId, OrderCommand command) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(command, "command");

        String symbol = validateSymbol(command.symbol());
        OrderSide side = parseSide(command.side());
        if (side == OrderSide.SELL) {
            throw new IllegalArgumentException("Sell orders are not supported in this prototype");
        }
        OrderType type = parseType(command.type());
        int quantity = parseQuantity(command.quantity());
        BigDecimal limitPrice = type == OrderType.LIMIT ? parseLimitPrice(command.limitPrice()) : null;

        Stock[] stockRef = new Stock[1];
        MarketDataSnapshot[] snapshotRef = new MarketDataSnapshot[1];

        OrderResult result = transactionManager.inTransaction(() -> {
            var account = authService.findAccount(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));
            if (account.getState() != AccountState.ACTIVE) {
                throw new IllegalStateException("Account is not active");
            }

            if (command.clientOrderId() != null) {
                Optional<TradeOrder> existing = orderRepository.findByClientOrderId(accountId, command.clientOrderId());
                if (existing.isPresent()) {
                    return toResult(existing.get());
                }
            }

            Stock stock = stockRepository.findBySymbol(symbol)
                    .orElseThrow(() -> new IllegalArgumentException("Symbole inconnu: " + symbol));
            stockRef[0] = stock;

            MarketDataSnapshot snapshot = marketDataService.tickFor(stock.getSymbol(), stock.getLastPrice());
            snapshotRef[0] = snapshot;
            stock.updatePrice(snapshot.price(), snapshot.timestamp());
            stockRepository.updatePrice(stock.getId(), stock);

            runPreTradeChecks(accountId, type, quantity, limitPrice, snapshot.price());

            if (type == OrderType.MARKET) {
                return placeImmediateExecution(accountId, stock, side, type, quantity, command.clientOrderId(), null, snapshot);
            }

            BigDecimal reservedNotional = limitPrice
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lastPrice = scale(snapshot.price());
            if (lastPrice.compareTo(limitPrice) >= 0) {
                return placeImmediateExecution(accountId, stock, side, type, quantity, command.clientOrderId(), limitPrice, snapshot);
            }

            return placePendingLimit(accountId, stock, side, quantity, command.clientOrderId(), limitPrice, reservedNotional);
        });

        if (stockRef[0] != null && snapshotRef[0] != null) {
            onMarketTick(stockRef[0].getId(), stockRef[0].getSymbol(), snapshotRef[0].price(), snapshotRef[0].timestamp());
        }

        return result;
    }

    public List<OrderResult> listOrders(UUID accountId) {
        return orderRepository.findByAccount(accountId).stream()
                .map(this::toResult)
                .toList();
    }

    public OrderResult cancelOrder(UUID accountId, UUID orderId) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(orderId, "orderId");

        return transactionManager.inTransaction(() -> {
            TradeOrder order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));
            if (!order.accountId().equals(accountId)) {
                throw new IllegalArgumentException("Order does not belong to this account");
            }
            if (!order.isPending()) {
                throw new IllegalStateException("Only pending orders can be cancelled");
            }
            if (order.notional() != null && order.notional().signum() > 0) {
                walletService.refund(accountId, order.notional());
            }
            TradeOrder cancelled = order.cancel(Instant.now(), "Annule par le client");
            orderRepository.update(cancelled);
            audit(cancelled, "ORDER_CANCELLED", "\"reason\":\"CLIENT_REQUEST\"");
            AppMetrics.recordOrder(cancelled.type().name(), cancelled.status().name());
            notifyOrder(cancelled, "ORDER_CANCELLED",
                    "Ordre %s annule".formatted(cancelled.symbol()),
                    "{\"status\":\"CANCELLED\"}");
            return toResult(cancelled);
        });
    }

    public void onMarketTick(UUID stockId, String symbol, BigDecimal price, Instant timestamp) {
        List<TradeOrder> pendingOrders = orderRepository.findPendingByStock(stockId);
        if (pendingOrders.isEmpty()) {
            return;
        }
        BigDecimal scaledPrice = scale(price);
        Instant effectiveTimestamp = timestamp != null ? timestamp : Instant.now();

        for (TradeOrder pending : pendingOrders) {
            BigDecimal limit = pending.limitPrice();
            if (limit == null) {
                continue;
            }
            if (scaledPrice.compareTo(limit) >= 0) {
                try {
                    transactionManager.inTransaction(() -> {
                        processPendingOrder(pending.id(), scaledPrice, effectiveTimestamp);
                        return null;
                    });
                } catch (RuntimeException ignored) {
                    // any failure is handled inside processPendingOrder (audit + wallet refunds)
                }
            }
        }
    }

    private OrderResult placeImmediateExecution(
            UUID accountId,
            Stock stock,
            OrderSide side,
            OrderType type,
            int quantity,
            String clientOrderId,
            BigDecimal limitPrice,
            MarketDataSnapshot snapshot
    ) {
        BigDecimal executionPrice = scale(snapshot.price());
        BigDecimal notional = executionPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        Instant executedAt = snapshot.timestamp() != null ? snapshot.timestamp() : now;
        UUID orderId = UUID.randomUUID();

        TradeOrder order;
        try {
            walletService.debit(accountId, notional);
            order = new TradeOrder(
                    orderId,
                    accountId,
                    stock.getId(),
                    stock.getSymbol(),
                    side,
                    type,
                    quantity,
                    limitPrice,
                    executionPrice,
                    notional,
                    clientOrderId,
                    OrderStatus.COMPLETED,
                    now,
                    now,
                    executedAt,
                    null
            );
            orderRepository.save(order);
            AppMetrics.recordOrder(order.type().name(), order.status().name());
            applyPositionFill(order);
            String attributes = buildFillAuditAttributes(notional, executionPrice, limitPrice);
            audit(order, "ORDER_COMPLETED", attributes);
            notifyOrder(order, "ORDER_COMPLETED",
                    "Ordre %s rempli (%d)".formatted(order.symbol(), order.quantity()),
                    "{" + attributes + "}");
        } catch (IllegalArgumentException ex) {
            order = new TradeOrder(
                    orderId,
                    accountId,
                    stock.getId(),
                    stock.getSymbol(),
                    side,
                    type,
                    quantity,
                    limitPrice,
                    executionPrice,
                    notional,
                    clientOrderId,
                    OrderStatus.FAILED,
                    now,
                    now,
                    executedAt,
                    ex.getMessage()
            );
            orderRepository.save(order);
            AppMetrics.recordOrder(order.type().name(), order.status().name());
            String reason = "\"reason\":\"" + escape(ex.getMessage()) + "\"";
            audit(order, "ORDER_FAILED", reason);
            notifyOrder(order, "ORDER_FAILED",
                    "Ordre %s echoue".formatted(order.symbol()),
                    "{" + reason + "}");
        }
        return toResult(order);
    }

    private OrderResult placePendingLimit(
            UUID accountId,
            Stock stock,
            OrderSide side,
            int quantity,
            String clientOrderId,
            BigDecimal limitPrice,
            BigDecimal reservedNotional
    ) {
        walletService.debit(accountId, reservedNotional);
        TradeOrder pending = createPendingLimit(
                accountId,
                stock,
                side,
                quantity,
                clientOrderId,
                limitPrice,
                reservedNotional
        );
        orderRepository.save(pending);
        String auditPayload = "\"limitPrice\":%s,\"reservedNotional\":%s"
                .formatted(limitPrice.toPlainString(), reservedNotional.toPlainString());
        AppMetrics.recordOrder(pending.type().name(), pending.status().name());
        audit(pending, "ORDER_PENDING", auditPayload);
        notifyOrder(pending, "ORDER_PENDING",
                "Ordre %s en attente".formatted(pending.symbol()),
                "{" + auditPayload + "}");
        return toResult(pending);
    }

    private void processPendingOrder(UUID orderId, BigDecimal executionPrice, Instant executedAt) {
        Optional<TradeOrder> currentOpt = orderRepository.findById(orderId);
        if (currentOpt.isEmpty()) {
            return;
        }
        TradeOrder current = currentOpt.get();
        if (!current.isPending()) {
            return;
        }
        BigDecimal reserved = current.notional() != null ? current.notional() : BigDecimal.ZERO;
        BigDecimal actual = executionPrice.multiply(BigDecimal.valueOf(current.quantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = actual.subtract(reserved).setScale(2, RoundingMode.HALF_UP);
        boolean extraDebited = false;
        try {
            if (difference.signum() > 0) {
                walletService.debit(current.accountId(), difference);
                extraDebited = true;
            }
            TradeOrder completed = current.complete(executionPrice, executedAt);
            orderRepository.update(completed);
            if (difference.signum() < 0) {
                walletService.refund(completed.accountId(), difference.abs());
            }
            AppMetrics.recordOrder(completed.type().name(), completed.status().name());
            String attributes = buildFillAuditAttributes(actual, executionPrice, current.limitPrice());
            applyPositionFill(completed);
            audit(completed, "ORDER_COMPLETED", attributes);
            notifyOrder(completed, "ORDER_COMPLETED",
                    "Ordre %s rempli (%d)".formatted(completed.symbol(), completed.quantity()),
                    "{" + attributes + "}");
        } catch (RuntimeException ex) {
            if (extraDebited && difference.signum() > 0) {
                walletService.refund(current.accountId(), difference.abs());
            }
            if (reserved.signum() > 0) {
                walletService.refund(current.accountId(), reserved);
            }
            TradeOrder failed = current.fail(ex.getMessage(), executionPrice, executedAt);
            orderRepository.update(failed);
            AppMetrics.recordOrder(failed.type().name(), failed.status().name());
            String reason = "\"reason\":\"" + escape(ex.getMessage()) + "\"";
            audit(failed, "ORDER_FAILED", reason);
            notifyOrder(failed, "ORDER_FAILED",
                    "Ordre %s echoue".formatted(failed.symbol()),
                    "{" + reason + "}");
        }
    }

    private String buildFillAuditAttributes(BigDecimal notional, BigDecimal executionPrice, BigDecimal limitPrice) {
        StringBuilder builder = new StringBuilder();
        builder.append("\"notional\":").append(notional.toPlainString())
                .append(",\"fillPrice\":").append(executionPrice.toPlainString());
        if (limitPrice != null) {
            builder.append(",\"limitPrice\":").append(limitPrice.toPlainString());
        }
        return builder.toString();
    }

    private void applyPositionFill(TradeOrder order) {
        if (order.status() != OrderStatus.COMPLETED || order.executedPrice() == null) {
            return;
        }
        Position updated = positionRepository.find(order.accountId(), order.stockId())
                .map(position -> position.withFill(order.executedPrice(), order.quantity(), order.executedAt()))
                .orElseGet(() -> Position.empty(order.accountId(), order.stockId())
                        .withFill(order.executedPrice(), order.quantity(), order.executedAt()));
        positionRepository.upsert(updated);
    }

    private void audit(TradeOrder order, String eventType, String extraAttributes) {
        if (orderAuditRepository == null) {
            return;
        }
        StringBuilder payload = new StringBuilder();
        payload.append("{\"accountId\":\"").append(order.accountId()).append("\"")
                .append(",\"symbol\":\"").append(order.symbol()).append("\"")
                .append(",\"status\":\"").append(order.status()).append("\"");
        if (extraAttributes != null && !extraAttributes.isBlank()) {
            payload.append(",").append(extraAttributes);
        }
        payload.append("}");
        orderAuditRepository.append(new OrderAuditEntry(order.id(), eventType, payload.toString(), Instant.now()));
    }

    private void notifyOrder(TradeOrder order, String category, String message, String payload) {
        Map<String, Object> fields = Map.of(
                "orderId", order.id().toString(),
                "accountId", order.accountId().toString(),
                "category", category,
                "status", order.status().name(),
                "symbol", order.symbol()
        );
        logger.info("order_event", fields);
        if (notificationService == null) {
            return;
        }
        try {
            notificationService.publish(
                    order.accountId(),
                    category,
                    message,
                    order.id().toString(),
                    payload
            );
        } catch (RuntimeException ex) {
            logger.error("order_notification_failed", ex, fields);
        }
    }

    private String validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        return symbol.trim().toUpperCase();
    }

    private OrderSide parseSide(String value) {
        try {
            return OrderSide.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported side: " + value);
        }
    }

    private OrderType parseType(String value) {
        try {
            return OrderType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported order type: " + value);
        }
    }

    private int parseQuantity(String quantity) {
        try {
            int qty = Integer.parseInt(quantity);
            if (qty <= 0) {
                throw new NumberFormatException();
            }
            return qty;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Quantity must be a positive integer");
        }
    }

    private BigDecimal parseLimitPrice(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Limit price is required for LIMIT orders");
        }
        try {
            BigDecimal price = new BigDecimal(raw);
            if (price.signum() <= 0) {
                throw new NumberFormatException();
            }
            return scale(price);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid limit price: " + raw);
        }
    }

    private OrderResult toResult(TradeOrder order) {
        return new OrderResult(
                order.id(),
                order.stockId(),
                order.symbol(),
                order.type(),
                order.side(),
                order.quantity(),
                order.limitPrice(),
                order.executedPrice(),
                order.notional(),
                order.status(),
                order.createdAt(),
                order.updatedAt(),
                order.executedAt(),
                order.failureReason()
        );
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void runPreTradeChecks(UUID accountId,
                                   OrderType type,
                                   int quantity,
                                   BigDecimal limitPrice,
                                   BigDecimal marketPrice) {
        if (quantity > 1_000) {
            throw new IllegalArgumentException("Quantite maximale autorisee: 1000 actions");
        }
        BigDecimal referencePrice = type == OrderType.LIMIT ? limitPrice : scale(marketPrice);
        if (referencePrice == null || referencePrice.signum() <= 0) {
            throw new IllegalArgumentException("Prix de reference indisponible");
        }
        BigDecimal notional = referencePrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        walletService.ensureBalance(accountId, notional);
        if (type == OrderType.LIMIT && marketPrice != null) {
            BigDecimal scaledMarket = scale(marketPrice);
            BigDecimal upper = scaledMarket.multiply(BigDecimal.valueOf(1.5)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lower = scaledMarket.multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
            if (referencePrice.compareTo(lower) < 0 || referencePrice.compareTo(upper) > 0) {
                throw new IllegalArgumentException("Limit price doit rester dans une bande de +/-50% du marché");
            }
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private TradeOrder createPendingLimit(
            UUID accountId,
            Stock stock,
            OrderSide side,
            int quantity,
            String clientOrderId,
            BigDecimal limitPrice,
            BigDecimal reservedNotional
    ) {
        Instant now = Instant.now();
        return new TradeOrder(
                UUID.randomUUID(),
                accountId,
                stock.getId(),
                stock.getSymbol(),
                side,
                OrderType.LIMIT,
                quantity,
                limitPrice,
                null,
                reservedNotional,
                clientOrderId,
                OrderStatus.PENDING,
                now,
                now,
                null,
                null
        );
    }

    public record OrderCommand(
            String symbol,
            String side,
            String type,
            String quantity,
            String limitPrice,
            String clientOrderId
    ) { }

    public record OrderResult(
            UUID orderId,
            UUID stockId,
            String symbol,
            OrderType type,
            OrderSide side,
            int quantity,
            BigDecimal limitPrice,
            BigDecimal executedPrice,
            BigDecimal notional,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant executedAt,
            String failureReason
    ) { }
}
