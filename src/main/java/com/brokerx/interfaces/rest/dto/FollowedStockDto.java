package com.brokerx.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FollowedStockDto(
        UUID stockId,
        String symbol,
        String name,
        BigDecimal price,
        Instant updatedAt
) {
}
