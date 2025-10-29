package com.brokerx.adapters.persistence.jdbc;

import com.brokerx.domain.position.Position;
import com.brokerx.ports.PositionRepository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PositionRepositoryJdbc implements PositionRepository {
    private final DataSource dataSource;

    public PositionRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Position> find(UUID accountId, UUID stockId) {
        var sql = """
            SELECT account_id, stock_id, quantity, average_price, updated_at
              FROM positions
             WHERE account_id = ? AND stock_id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            ps.setObject(2, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load position", e);
        }
    }

    @Override
    public void upsert(Position position) {
        var sql = """
            INSERT INTO positions (account_id, stock_id, quantity, average_price, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (account_id, stock_id)
            DO UPDATE SET quantity = EXCLUDED.quantity,
                          average_price = EXCLUDED.average_price,
                          updated_at = EXCLUDED.updated_at
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, position.accountId());
            ps.setObject(2, position.stockId());
            ps.setBigDecimal(3, position.quantity());
            ps.setBigDecimal(4, position.averagePrice());
            ps.setTimestamp(5, Timestamp.from(position.updatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert position", e);
        }
    }

    @Override
    public List<Position> listByAccount(UUID accountId) {
        var sql = """
            SELECT account_id, stock_id, quantity, average_price, updated_at
              FROM positions
             WHERE account_id = ?
             ORDER BY stock_id
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Position> positions = new ArrayList<>();
                while (rs.next()) {
                    positions.add(mapRow(rs));
                }
                return positions;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to list positions", e);
        }
    }

    private Position mapRow(ResultSet rs) throws SQLException {
        UUID accountId = rs.getObject("account_id", UUID.class);
        UUID stockId = rs.getObject("stock_id", UUID.class);
        var quantity = rs.getBigDecimal("quantity");
        var averagePrice = rs.getBigDecimal("average_price");
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return new Position(accountId, stockId, quantity, averagePrice, updatedAt);
    }
}
