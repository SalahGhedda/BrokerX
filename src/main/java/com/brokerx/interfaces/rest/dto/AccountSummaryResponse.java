package com.brokerx.interfaces.rest.dto;

import com.brokerx.domain.account.AccountState;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AccountSummaryResponse(
        UUID accountId,
        String email,
        AccountState state,
        BigDecimal balance,
        List<FollowedStockDto> followedStocks
) {
}
