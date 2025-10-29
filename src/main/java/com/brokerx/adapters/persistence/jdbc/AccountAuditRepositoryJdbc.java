package com.brokerx.adapters.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

import com.brokerx.ports.AccountAuditRepository;

public class AccountAuditRepositoryJdbc implements AccountAuditRepository {
    private final DataSource dataSource;

    public AccountAuditRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(UUID accountId, String action, String metadataJson) {
        var sql = """
            INSERT INTO account_audit (id, account_id, action, metadata)
            VALUES (?, ?, ?, ?)
        """;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, accountId);
            ps.setString(3, action);
            ps.setString(4, metadataJson == null ? "{}" : metadataJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to record account audit", e);
        }
    }
}
