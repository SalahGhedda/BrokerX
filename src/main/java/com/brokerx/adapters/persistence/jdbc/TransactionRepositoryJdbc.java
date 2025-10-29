package com.brokerx.adapters.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.brokerx.domain.wallet.Transaction;
import com.brokerx.ports.TransactionRepository;

public class TransactionRepositoryJdbc implements TransactionRepository {
    private final DataSource dataSource;

    public TransactionRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String key) {
        var sql = """
            SELECT id, wallet_id, amount, type, state, idempotency_key, occurred_at
            FROM transactions
            WHERE idempotency_key = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load transaction by idempotency key", e);
        }
    }

    @Override
    public void append(Transaction tx) {
        var updateSql = """
            UPDATE transactions
               SET state = ?,
                   occurred_at = ?
             WHERE id = ?
        """;
        var insertSql = """
            INSERT INTO transactions (id, wallet_id, amount, type, state, idempotency_key, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (var handle = ConnectionHandle.acquire(dataSource)) {
            Connection connection = handle.connection();
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, tx.getState());
                update.setTimestamp(2, Timestamp.from(tx.getOccurredAt()));
                update.setObject(3, tx.getId());
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setObject(1, tx.getId());
                insert.setObject(2, tx.getWalletId());
                insert.setBigDecimal(3, tx.getAmount());
                insert.setString(4, tx.getType());
                insert.setString(5, tx.getState());
                insert.setString(6, tx.getIdempotencyKey());
                insert.setTimestamp(7, Timestamp.from(tx.getOccurredAt()));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to save transaction", e);
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID walletId = rs.getObject("wallet_id", UUID.class);
        var amount = rs.getBigDecimal("amount");
        String type = rs.getString("type");
        String state = rs.getString("state");
        String idem = rs.getString("idempotency_key");
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
        return new Transaction(id, walletId, amount, type, state, idem, occurredAt);
    }
}
