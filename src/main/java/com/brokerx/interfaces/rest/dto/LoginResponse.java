package com.brokerx.interfaces.rest.dto;

import com.brokerx.domain.account.AccountState;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        UUID accountId,
        String email,
        AccountState state,
        String token,
        String tokenType,
        Instant issuedAt,
        Instant expiresAt
) {
}
