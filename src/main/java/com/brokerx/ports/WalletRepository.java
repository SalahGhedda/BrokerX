package com.brokerx.ports;
import java.util.Optional;
import java.util.UUID;
import com.brokerx.domain.wallet.Wallet;


public interface WalletRepository {
    Optional<Wallet> findByOwnerId(UUID ownerId);
    Wallet create(UUID ownerId);
    void update(Wallet wallet);
}
