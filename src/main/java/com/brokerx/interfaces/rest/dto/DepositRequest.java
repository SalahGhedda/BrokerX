package com.brokerx.interfaces.rest.dto;

import java.math.BigDecimal;

public record DepositRequest(
        BigDecimal amount,
        String idempotencyKey
) {
}
