package com.brokerx.adapters.persistence.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.brokerx.domain.wallet.Transaction;
import com.brokerx.ports.TransactionRepository;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<String, Transaction> txByKey = new HashMap<>();
    public Optional<Transaction> findByIdempotencyKey(String key) { return Optional.ofNullable(txByKey.get(key)); }
    public void append(Transaction tx) { txByKey.put(tx.getIdempotencyKey(), tx); }
}
