package com.brokerx.interfaces.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositResponse(
        UUID transactionId,
        BigDecimal amount,
        BigDecimal balance
) {
}
