package com.brokerx.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record SignupResponse(
        UUID accountId,
        String email,
        String verificationCode,
        Instant expiresAt
) {
}
