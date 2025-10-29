package com.brokerx.adapters.persistence.jdbc;

import com.brokerx.domain.order.OrderSide;
import com.brokerx.domain.order.OrderStatus;
import com.brokerx.domain.order.OrderType;
import com.brokerx.domain.order.TradeOrder;
import com.brokerx.ports.OrderRepository;

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

public class OrderRepositoryJdbc implements OrderRepository {
    private final DataSource dataSource;

    public OrderRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(TradeOrder order) {
        var sql = """
            INSERT INTO orders (
                id,
                account_id,
                stock_id,
                symbol,
                side,
                type,
                quantity,
                limit_price,
                executed_price,
                notional,
                client_order_id,
                status,
                failure_reason,
                created_at,
                updated_at,
                executed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            bindAll(ps, order);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to insert order", e);
        }
    }

    @Override
    public void update(TradeOrder order) {
        var sql = """
            UPDATE orders
               SET account_id = ?,
                   stock_id = ?,
                   symbol = ?,
                   side = ?,
                   type = ?,
                   quantity = ?,
                   limit_price = ?,
                   executed_price = ?,
                   notional = ?,
                   client_order_id = ?,
                   status = ?,
                   failure_reason = ?,
                   created_at = ?,
                   updated_at = ?,
                   executed_at = ?
             WHERE id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            bindAll(ps, order);
            ps.setObject(16, order.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to update order", e);
        }
    }

    @Override
    public Optional<TradeOrder> findById(UUID orderId) {
        var sql = """
            SELECT id, account_id, stock_id, symbol, side, type, quantity,
                   limit_price, executed_price, notional, client_order_id,
                   status, failure_reason, created_at, updated_at, executed_at
              FROM orders
             WHERE id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load order", e);
        }
    }

    @Override
    public Optional<TradeOrder> findByClientOrderId(UUID accountId, String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return Optional.empty();
        }
        var sql = """
            SELECT id, account_id, stock_id, symbol, side, type, quantity,
                   limit_price, executed_price, notional, client_order_id,
                   status, failure_reason, created_at, updated_at, executed_at
              FROM orders
             WHERE account_id = ? AND client_order_id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            ps.setString(2, clientOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load order by client id", e);
        }
    }

    @Override
    public List<TradeOrder> findPendingByStock(UUID stockId) {
        var sql = """
            SELECT id, account_id, stock_id, symbol, side, type, quantity,
                   limit_price, executed_price, notional, client_order_id,
                   status, failure_reason, created_at, updated_at, executed_at
              FROM orders
             WHERE stock_id = ? AND status = 'PENDING'
             ORDER BY created_at ASC
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TradeOrder> orders = new ArrayList<>();
                while (rs.next()) {
                    orders.add(mapRow(rs));
                }
                return orders;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to list pending orders", e);
        }
    }

    @Override
    public List<TradeOrder> findByAccount(UUID accountId) {
        var sql = """
            SELECT id, account_id, stock_id, symbol, side, type, quantity,
                   limit_price, executed_price, notional, client_order_id,
                   status, failure_reason, created_at, updated_at, executed_at
              FROM orders
             WHERE account_id = ?
             ORDER BY created_at DESC
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TradeOrder> orders = new ArrayList<>();
                while (rs.next()) {
                    orders.add(mapRow(rs));
                }
                return orders;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to list orders by account", e);
        }
    }

    @Override
    public List<TradeOrder> findAll() {
        var sql = """
            SELECT id, account_id, stock_id, symbol, side, type, quantity,
                   limit_price, executed_price, notional, client_order_id,
                   status, failure_reason, created_at, updated_at, executed_at
              FROM orders
             ORDER BY created_at DESC
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<TradeOrder> orders = new ArrayList<>();
                while (rs.next()) {
                    orders.add(mapRow(rs));
                }
                return orders;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to list all orders", e);
        }
    }

    private void bindAll(PreparedStatement ps, TradeOrder order) throws SQLException {
        ps.setObject(1, order.id());
        ps.setObject(2, order.accountId());
        ps.setObject(3, order.stockId());
        ps.setString(4, order.symbol());
        ps.setString(5, order.side().name());
        ps.setString(6, order.type().name());
        ps.setInt(7, order.quantity());
        if (order.limitPrice() != null) {
            ps.setBigDecimal(8, order.limitPrice());
        } else {
            ps.setNull(8, java.sql.Types.NUMERIC);
        }
        if (order.executedPrice() != null) {
            ps.setBigDecimal(9, order.executedPrice());
        } else {
            ps.setNull(9, java.sql.Types.NUMERIC);
        }
        if (order.notional() != null) {
            ps.setBigDecimal(10, order.notional());
        } else {
            ps.setNull(10, java.sql.Types.NUMERIC);
        }
        if (order.clientOrderId() != null) {
            ps.setString(11, order.clientOrderId());
        } else {
            ps.setNull(11, java.sql.Types.VARCHAR);
        }
        ps.setString(12, order.status().name());
        if (order.failureReason() != null) {
            ps.setString(13, order.failureReason());
        } else {
            ps.setNull(13, java.sql.Types.VARCHAR);
        }
        ps.setTimestamp(14, Timestamp.from(order.createdAt()));
        ps.setTimestamp(15, Timestamp.from(order.updatedAt()));
        if (order.executedAt() != null) {
            ps.setTimestamp(16, Timestamp.from(order.executedAt()));
        } else {
            ps.setNull(16, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    private TradeOrder mapRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID accountId = rs.getObject("account_id", UUID.class);
        UUID stockId = rs.getObject("stock_id", UUID.class);
        String symbol = rs.getString("symbol");
        OrderSide side = OrderSide.valueOf(rs.getString("side"));
        OrderType type = OrderType.valueOf(rs.getString("type"));
        int quantity = rs.getInt("quantity");
        var limitPrice = rs.getBigDecimal("limit_price");
        var executedPrice = rs.getBigDecimal("executed_price");
        var notional = rs.getBigDecimal("notional");
        String clientOrderId = rs.getString("client_order_id");
        OrderStatus status = OrderStatus.valueOf(rs.getString("status"));
        String failureReason = rs.getString("failure_reason");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Timestamp execTs = rs.getTimestamp("executed_at");
        Instant executedAt = execTs != null ? execTs.toInstant() : null;
        return new TradeOrder(
                id,
                accountId,
                stockId,
                symbol,
                side,
                type,
                quantity,
                limitPrice,
                executedPrice,
                notional,
                clientOrderId,
                status,
                createdAt,
                updatedAt,
                executedAt,
                failureReason
        );
    }
}
