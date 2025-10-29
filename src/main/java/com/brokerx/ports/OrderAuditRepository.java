package com.brokerx.ports;

import com.brokerx.domain.order.OrderAuditEntry;

public interface OrderAuditRepository {
    void append(OrderAuditEntry entry);
}
