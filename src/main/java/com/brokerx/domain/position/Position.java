package com.brokerx.domain.position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record Position(
        UUID accountId,
        UUID stockId,
        BigDecimal quantity,
        BigDecimal averagePrice,
        Instant updatedAt
) {
    public Position withFill(BigDecimal executedPrice, int fillQuantity, Instant at) {
        if (executedPrice == null || fillQuantity <= 0) {
            throw new IllegalArgumentException("Executed price and quantity must be positive");
        }
        BigDecimal fillQty = BigDecimal.valueOf(fillQuantity);
        BigDecimal newQuantity = quantity.add(fillQty);
        BigDecimal newAverage;
        if (quantity.signum() == 0) {
            newAverage = executedPrice.setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal totalCost = averagePrice.multiply(quantity).add(executedPrice.multiply(fillQty));
            newAverage = totalCost.divide(newQuantity, 2, RoundingMode.HALF_UP);
        }
        return new Position(accountId, stockId, newQuantity, newAverage, at != null ? at : Instant.now());
    }

    public static Position empty(UUID accountId, UUID stockId) {
        return new Position(accountId, stockId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), Instant.now());
    }
}
