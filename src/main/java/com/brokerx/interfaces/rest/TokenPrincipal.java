package com.brokerx.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

public record TokenPrincipal(
        UUID accountId,
        Instant issuedAt,
        Instant expiresAt,
        String token
) {
    public static final String ATTRIBUTE = "brokerx.token.principal";
}
