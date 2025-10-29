package com.brokerx.adapters.persistence.jdbc;

import com.brokerx.domain.order.OrderAuditEntry;
import com.brokerx.ports.OrderAuditRepository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public class OrderAuditRepositoryJdbc implements OrderAuditRepository {
    private final DataSource dataSource;

    public OrderAuditRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(OrderAuditEntry entry) {
        var sql = """
            INSERT INTO order_audit (id, order_id, event_type, payload, created_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, entry.orderId());
            ps.setString(3, entry.eventType());
            ps.setString(4, entry.payload());
            ps.setTimestamp(5, Timestamp.from(entry.createdAt() != null ? entry.createdAt() : Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to append order audit", e);
        }
    }
}
