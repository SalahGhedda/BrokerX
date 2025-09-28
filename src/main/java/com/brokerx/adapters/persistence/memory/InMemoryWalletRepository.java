package com.brokerx.adapters.persistence.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.domain.wallet.Wallet;
import com.brokerx.ports.WalletRepository;

public class InMemoryWalletRepository implements WalletRepository {
    private final Map<UUID, Wallet> wallets = new HashMap<>();

    public Optional<Wallet> findByOwnerId(UUID ownerId) {
        return wallets.values().stream()
                .filter(w -> w.getOwnerId().equals(ownerId))
                .findFirst();
    }
    public Wallet create(UUID ownerId) {
        var wallet = new Wallet(UUID.randomUUID(), ownerId);
        wallets.put(wallet.getId(), wallet);
        return wallet;
    }
}
