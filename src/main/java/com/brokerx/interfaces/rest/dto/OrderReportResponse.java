package com.brokerx.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;

public record OrderReportResponse(
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byType,
        Instant generatedAt
) {
}
