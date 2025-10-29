package com.brokerx.adapters.persistence.memory;

import com.brokerx.domain.order.OrderAuditEntry;
import com.brokerx.ports.OrderAuditRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryOrderAuditRepository implements OrderAuditRepository {
    private final List<OrderAuditEntry> entries = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void append(OrderAuditEntry entry) {
        entries.add(entry);
    }

    public List<OrderAuditEntry> entries() {
        return List.copyOf(entries);
    }
}
