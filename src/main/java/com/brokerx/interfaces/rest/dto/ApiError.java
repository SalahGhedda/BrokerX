package com.brokerx.interfaces.rest.dto;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId
) {
}
