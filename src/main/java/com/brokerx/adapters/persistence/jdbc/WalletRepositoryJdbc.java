package com.brokerx.adapters.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.brokerx.domain.wallet.Wallet;
import com.brokerx.ports.WalletRepository;

public class WalletRepositoryJdbc implements WalletRepository {
    private final DataSource dataSource;

    public WalletRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Wallet> findByOwnerId(UUID ownerId) {
        var sql = """
            SELECT id, owner_id, balance
            FROM wallets
            WHERE owner_id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load wallet by owner id", e);
        }
    }

    @Override
    public Wallet create(UUID ownerId) {
        var walletId = UUID.randomUUID();
        var sql = "INSERT INTO wallets (id, owner_id, balance) VALUES (?, ?, ?)";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, walletId);
            ps.setObject(2, ownerId);
            ps.setBigDecimal(3, BigDecimal.ZERO);
            ps.executeUpdate();
            return new Wallet(walletId, ownerId, BigDecimal.ZERO);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to create wallet", e);
        }
    }

    @Override
    public void update(Wallet wallet) {
        var sql = "UPDATE wallets SET balance = ? WHERE id = ?";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setBigDecimal(1, wallet.getBalance());
            ps.setObject(2, wallet.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to update wallet balance", e);
        }
    }

    private Wallet mapRow(ResultSet rs) throws SQLException {
        UUID walletId = rs.getObject("id", UUID.class);
        UUID ownerId = rs.getObject("owner_id", UUID.class);
        BigDecimal balance = rs.getBigDecimal("balance");
        return new Wallet(walletId, ownerId, balance);
    }
}
