package com.brokerx.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String category,
        String message,
        String referenceId,
        Instant createdAt,
        String payload
) {
}
