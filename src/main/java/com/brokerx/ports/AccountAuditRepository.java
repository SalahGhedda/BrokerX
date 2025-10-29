package com.brokerx.ports;

import java.util.UUID;

public interface AccountAuditRepository {
    void record(UUID accountId, String action, String metadataJson);
}
