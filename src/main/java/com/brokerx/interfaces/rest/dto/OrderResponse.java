package com.brokerx.interfaces.rest.dto;

import com.brokerx.domain.order.OrderSide;
import com.brokerx.domain.order.OrderStatus;
import com.brokerx.domain.order.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
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
) {
}
