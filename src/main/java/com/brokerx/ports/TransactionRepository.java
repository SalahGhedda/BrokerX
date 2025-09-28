package com.brokerx.ports;
import java.util.Optional;
import java.util.UUID;
import com.brokerx.domain.wallet.Transaction;


public interface TransactionRepository {
    Optional<Transaction> findByIdempotencyKey(String key);
    void append(Transaction tx);
}