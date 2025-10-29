package com.brokerx.adapters.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.brokerx.domain.stock.Stock;
import com.brokerx.ports.StockRepository;

public class StockRepositoryJdbc implements StockRepository {
    private final DataSource dataSource;

    public StockRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Stock> findAll() {
        var sql = """
            SELECT id, symbol, name, description, last_price, updated_at
              FROM stocks
              ORDER BY symbol
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Stock> stocks = new ArrayList<>();
            while (rs.next()) {
                stocks.add(mapStock(rs));
            }
            return stocks;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to list stocks", e);
        }
    }

    @Override
    public Optional<Stock> findById(UUID stockId) {
        var sql = """
            SELECT id, symbol, name, description, last_price, updated_at
              FROM stocks
             WHERE id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapStock(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load stock", e);
        }
    }

    @Override
    public Optional<Stock> findBySymbol(String symbol) {
        var sql = """
            SELECT id, symbol, name, description, last_price, updated_at
              FROM stocks
             WHERE upper(symbol) = upper(?)
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapStock(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load stock by symbol", e);
        }
    }

    @Override
    public void updatePrice(UUID stockId, Stock stock) {
        var sql = "UPDATE stocks SET last_price = ?, updated_at = ? WHERE id = ?";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setBigDecimal(1, stock.getLastPrice());
            ps.setTimestamp(2, Timestamp.from(stock.getUpdatedAt()));
            ps.setObject(3, stockId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to update stock price", e);
        }
    }

    @Override
    public List<Stock> findFollowedByAccount(UUID accountId) {
        var sql = """
            SELECT s.id, s.symbol, s.name, s.description, s.last_price, s.updated_at
              FROM account_followed_stocks afs
              JOIN stocks s ON s.id = afs.stock_id
             WHERE afs.account_id = ?
             ORDER BY s.symbol
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Stock> stocks = new ArrayList<>();
                while (rs.next()) {
                    stocks.add(mapStock(rs));
                }
                return stocks;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to list followed stocks", e);
        }
    }

    @Override
    public void follow(UUID accountId, UUID stockId) {
        var sql = "INSERT INTO account_followed_stocks (account_id, stock_id, followed_at) VALUES (?, ?, ?)";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            ps.setObject(2, stockId);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // duplicate follow: ignore
            if (!"23505".equals(e.getSQLState())) {
                throw new PersistenceException("Failed to follow stock", e);
            }
        }
    }

    @Override
    public void unfollow(UUID accountId, UUID stockId) {
        var sql = "DELETE FROM account_followed_stocks WHERE account_id = ? AND stock_id = ?";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            ps.setObject(2, stockId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to unfollow stock", e);
        }
    }

    private Stock mapStock(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String symbol = rs.getString("symbol");
        String name = rs.getString("name");
        String description = rs.getString("description");
        BigDecimal price = rs.getBigDecimal("last_price");
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return new Stock(id, symbol, name, description, price, updatedAt);
    }
}
