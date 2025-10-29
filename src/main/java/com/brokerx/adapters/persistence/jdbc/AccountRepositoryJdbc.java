package com.brokerx.adapters.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.brokerx.domain.account.AccountState;
import com.brokerx.domain.account.UserAccount;
import com.brokerx.ports.AccountRepository;

public class AccountRepositoryJdbc implements AccountRepository {
    private final DataSource dataSource;

    public AccountRepositoryJdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        var sql = baseSelect() + " WHERE lower(email) = lower(?)";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to find account by email", e);
        }
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        var sql = baseSelect() + " WHERE phone = ?";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to find account by phone", e);
        }
    }

    @Override
    public Optional<UserAccount> findById(UUID accountId) {
        var sql = baseSelect() + " WHERE id = ?";
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load account", e);
        }
    }

    @Override
    public void save(UserAccount account) {
        var sql = """
            INSERT INTO accounts (
                id, email, phone, password_hash, full_name, address_line,
                date_of_birth, state, verification_code, verification_expires_at,
                rejection_reason, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            bindAccount(ps, account);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to insert account", e);
        }
    }

    @Override
    public void update(UserAccount account) {
        var sql = """
            UPDATE accounts
               SET phone = ?,
                   password_hash = ?,
                   full_name = ?,
                   address_line = ?,
                   date_of_birth = ?,
                   state = ?,
                   verification_code = ?,
                   verification_expires_at = ?,
                   rejection_reason = ?,
                   updated_at = ?
             WHERE id = ?
        """;
        try (var handle = ConnectionHandle.acquire(dataSource);
             PreparedStatement ps = handle.connection().prepareStatement(sql)) {
            ps.setString(1, account.getPhone());
            ps.setString(2, account.getPasswordHash());
            ps.setString(3, account.getFullName());
            ps.setString(4, account.getAddressLine());
            ps.setObject(5, account.getDateOfBirth());
            ps.setString(6, account.getState().name());
            ps.setString(7, account.getVerificationCode());
            ps.setTimestamp(8, Timestamp.from(account.getVerificationExpiresAt()));
            ps.setString(9, account.getRejectionReason());
            ps.setTimestamp(10, Timestamp.from(account.getUpdatedAt()));
            ps.setObject(11, account.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to update account", e);
        }
    }

    private String baseSelect() {
        return """
            SELECT id, email, phone, password_hash, full_name, address_line,
                   date_of_birth, state, verification_code, verification_expires_at,
                   rejection_reason, created_at, updated_at
              FROM accounts
        """;
    }

    private UserAccount mapRow(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        String passwordHash = rs.getString("password_hash");
        String fullName = rs.getString("full_name");
        String addressLine = rs.getString("address_line");
        LocalDate dob = rs.getObject("date_of_birth", LocalDate.class);
        AccountState state = AccountState.valueOf(rs.getString("state"));
        String verificationCode = rs.getString("verification_code");
        Instant verificationExpiresAt = rs.getTimestamp("verification_expires_at").toInstant();
        String rejectionReason = rs.getString("rejection_reason");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

        return new UserAccount(
                id,
                email,
                phone,
                passwordHash,
                fullName,
                addressLine,
                dob,
                state,
                verificationCode,
                verificationExpiresAt,
                rejectionReason,
                createdAt,
                updatedAt
        );
    }

    private void bindAccount(PreparedStatement ps, UserAccount account) throws SQLException {
        ps.setObject(1, account.getId());
        ps.setString(2, account.getEmail());
        ps.setString(3, account.getPhone());
        ps.setString(4, account.getPasswordHash());
        ps.setString(5, account.getFullName());
        ps.setString(6, account.getAddressLine());
        ps.setObject(7, account.getDateOfBirth());
        ps.setString(8, account.getState().name());
        ps.setString(9, account.getVerificationCode());
        ps.setTimestamp(10, Timestamp.from(account.getVerificationExpiresAt()));
        ps.setString(11, account.getRejectionReason());
        ps.setTimestamp(12, Timestamp.from(account.getCreatedAt()));
        ps.setTimestamp(13, Timestamp.from(account.getUpdatedAt()));
    }
}
