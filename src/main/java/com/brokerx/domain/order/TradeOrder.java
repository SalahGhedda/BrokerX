package com.brokerx.domain.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record TradeOrder(
        UUID id,
        UUID accountId,
        UUID stockId,
        String symbol,
        OrderSide side,
        OrderType type,
        int quantity,
        BigDecimal limitPrice,
        BigDecimal executedPrice,
        BigDecimal notional,
        String clientOrderId,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant executedAt,
        String failureReason
) {

    public static TradeOrder marketCompleted(
            UUID id,
            UUID accountId,
            UUID stockId,
            String symbol,
            OrderSide side,
            int quantity,
            BigDecimal executedPrice,
            BigDecimal notional,
            String clientOrderId,
            Instant timestamp
    ) {
        BigDecimal scaledPrice = scale(executedPrice);
        BigDecimal scaledNotional = scale(notional);
        Instant now = timestamp != null ? timestamp : Instant.now();
        return new TradeOrder(
                id,
                accountId,
                stockId,
                symbol,
                side,
                OrderType.MARKET,
                quantity,
                null,
                scaledPrice,
                scaledNotional,
                clientOrderId,
                OrderStatus.COMPLETED,
                now,
                now,
                now,
                null
        );
    }

    public static TradeOrder limitPending(
            UUID id,
            UUID accountId,
            UUID stockId,
            String symbol,
            OrderSide side,
            int quantity,
            BigDecimal limitPrice,
            String clientOrderId,
            Instant createdAt
    ) {
        Instant now = createdAt != null ? createdAt : Instant.now();
        return new TradeOrder(
                id,
                accountId,
                stockId,
                symbol,
                side,
                OrderType.LIMIT,
                quantity,
                scale(limitPrice),
                null,
                null,
                clientOrderId,
                OrderStatus.PENDING,
                now,
                now,
                null,
                null
        );
    }

    public TradeOrder complete(BigDecimal executionPrice, Instant executedAt) {
        BigDecimal scaledPrice = scale(executionPrice);
        BigDecimal scaledNotional = scaledPrice.multiply(BigDecimal.valueOf(quantity())).setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        Instant effectiveExecution = executedAt != null ? executedAt : now;
        return new TradeOrder(
                id,
                accountId,
                stockId,
                symbol,
                side,
                type,
                quantity(),
                limitPrice,
                scaledPrice,
                scaledNotional,
                clientOrderId,
                OrderStatus.COMPLETED,
                createdAt,
                now,
                effectiveExecution,
                null
        );
    }

    public TradeOrder fail(String reason, BigDecimal attemptedPrice, Instant executedAt) {
        BigDecimal scaledPrice = attemptedPrice != null ? scale(attemptedPrice) : null;
        BigDecimal scaledNotional = scaledPrice != null
                ? scaledPrice.multiply(BigDecimal.valueOf(quantity())).setScale(2, RoundingMode.HALF_UP)
                : null;
        Instant now = Instant.now();
        Instant effectiveExecution = executedAt != null ? executedAt : now;
        return new TradeOrder(
                id,
                accountId,
                stockId,
                symbol,
                side,
                type,
                quantity(),
                limitPrice,
                scaledPrice,
                scaledNotional,
                clientOrderId,
                OrderStatus.FAILED,
                createdAt,
                now,
                effectiveExecution,
                reason
        );
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public TradeOrder cancel(Instant cancelledAt, String reason) {
        Instant now = cancelledAt != null ? cancelledAt : Instant.now();
        return new TradeOrder(
                id,
                accountId,
                stockId,
                symbol,
                side,
                type,
                quantity(),
                limitPrice,
                executedPrice,
                notional,
                clientOrderId,
                OrderStatus.CANCELLED,
                createdAt,
                now,
                now,
                reason
        );
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
