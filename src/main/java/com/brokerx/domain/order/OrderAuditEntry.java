package com.brokerx.domain.order;

import java.time.Instant;
import java.util.UUID;

public record OrderAuditEntry(
        UUID orderId,
        String eventType,
        String payload,
        Instant createdAt
) {
}
