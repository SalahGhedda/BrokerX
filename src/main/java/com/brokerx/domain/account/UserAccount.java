package com.brokerx.domain.account;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class UserAccount {
    private final UUID id;
    private final String email;
    private final String phone;
    private final String passwordHash;
    private final String fullName;
    private final String addressLine;
    private final LocalDate dateOfBirth;
    private AccountState state;
    private String verificationCode;
    private Instant verificationExpiresAt;
    private String rejectionReason;
    private final Instant createdAt;
    private Instant updatedAt;

    public UserAccount(
            UUID id,
            String email,
            String phone,
            String passwordHash,
            String fullName,
            String addressLine,
            LocalDate dateOfBirth,
            AccountState state,
            String verificationCode,
            Instant verificationExpiresAt,
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.phone = Objects.requireNonNull(phone);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = Objects.requireNonNull(fullName);
        this.addressLine = Objects.requireNonNull(addressLine);
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth);
        this.state = Objects.requireNonNull(state);
        this.verificationCode = Objects.requireNonNull(verificationCode);
        this.verificationExpiresAt = Objects.requireNonNull(verificationExpiresAt);
        this.rejectionReason = rejectionReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getAddressLine() { return addressLine; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public AccountState getState() { return state; }
    public String getVerificationCode() { return verificationCode; }
    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void refreshVerification(String newCode, Instant expiresAt) {
        this.verificationCode = Objects.requireNonNull(newCode);
        this.verificationExpiresAt = Objects.requireNonNull(expiresAt);
        touch();
    }

    public void activate(String providedCode, Instant now) {
        requirePending();
        if (!Objects.equals(this.verificationCode, providedCode)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        if (verificationExpiresAt.isBefore(now)) {
            throw new IllegalArgumentException("Verification code expired");
        }
        this.state = AccountState.ACTIVE;
        this.verificationCode = providedCode;
        this.rejectionReason = null;
        touch();
    }

    public void reject(String reason) {
        requirePending();
        this.state = AccountState.SUSPENDED;
        this.rejectionReason = Objects.requireNonNullElse(reason, "Rejected");
        touch();
    }

    public void deactivate() {
        this.state = AccountState.SUSPENDED;
        touch();
    }

    public void reactivate() {
        this.state = AccountState.ACTIVE;
        this.rejectionReason = null;
        touch();
    }

    private void requirePending() {
        if (this.state != AccountState.PENDING) {
            throw new IllegalStateException("Account is not pending verification");
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
