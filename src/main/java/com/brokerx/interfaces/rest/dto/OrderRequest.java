package com.brokerx.interfaces.rest.dto;

import java.math.BigDecimal;

public record OrderRequest(
        String symbol,
        String side,
        String type,
        Integer quantity,
        BigDecimal limitPrice,
        String clientOrderId
) {
}
