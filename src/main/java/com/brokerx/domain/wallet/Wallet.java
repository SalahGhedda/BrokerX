package com.brokerx.domain.wallet;

import java.math.BigDecimal;
import java.util.UUID;

public class Wallet {
    private UUID id;
    private UUID ownerId;
    private BigDecimal balance;

    public Wallet(UUID id, UUID ownerId) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = BigDecimal.ZERO;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public BigDecimal getBalance() { return balance; }

    public void credit(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        this.balance = this.balance.add(amount);
    }
}
