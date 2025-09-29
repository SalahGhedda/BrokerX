package com.brokerx.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {
    private UUID id;
    private UUID walletId;
    private BigDecimal amount;
    private String type;     // "CREDIT" | "DEBIT"
    private String state;    // "PENDING" | "SETTLED" | "FAILED"
    private String idempotencyKey;
    private Instant occurredAt;

    public Transaction(UUID id, UUID walletId, BigDecimal amount, String type,
                        String state, String idempotencyKey, Instant occurredAt) {
        this.id = id;
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.state = state;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public static Transaction pending(UUID walletId, BigDecimal amount, String idem) {
        return new Transaction(UUID.randomUUID(), walletId, amount, "CREDIT", "PENDING", idem, Instant.now());
        // (Phase 1 : dépôts uniquement → CREDIT)
    }

    public Transaction settled() {
        return new Transaction(this.id, this.walletId, this.amount, this.type, "SETTLED", this.idempotencyKey, Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public String getState() { return state; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getOccurredAt() { return occurredAt; }

    public void setState(String state) { this.state = state; }
}
