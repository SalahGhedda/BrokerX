package com.brokerx.adapters.persistence.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.brokerx.ports.AccountAuditRepository;

public class InMemoryAccountAuditRepository implements AccountAuditRepository {
    private final List<Entry> entries = new ArrayList<>();

    @Override
    public synchronized void record(UUID accountId, String action, String metadataJson) {
        entries.add(new Entry(accountId, action, metadataJson, Instant.now()));
    }

    public record Entry(UUID accountId, String action, String metadata, Instant occurredAt) { }
}
