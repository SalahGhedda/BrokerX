package com.brokerx.application;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.brokerx.adapters.external.PaymentAdapterStub;
import com.brokerx.adapters.persistence.memory.InMemoryAccountRepository;
import com.brokerx.adapters.persistence.memory.InMemoryTransactionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryWalletRepository;
import com.brokerx.adapters.persistence.memory.NoopTransactionManager;

public class WalletServiceTest {
    @Test
    void depositShouldBeIdempotent() {
        var accountRepo = new InMemoryAccountRepository();
        var walletRepo  = new InMemoryWalletRepository();
        var txRepo      = new InMemoryTransactionRepository();
        var payment     = new PaymentAdapterStub();

        var walletService = new WalletService(walletRepo, txRepo, payment, new NoopTransactionManager());

        UUID owner = UUID.randomUUID();
        walletRepo.create(owner);

        var tx1 = walletService.deposit(owner, "SAME_KEY", 50.0);
        var tx2 = walletService.deposit(owner, "SAME_KEY", 50.0);

        assertEquals(tx1.getId(), tx2.getId(), "Doit renvoyer la même transaction");
        assertEquals(walletRepo.findByOwnerId(owner).get().getBalance().doubleValue(), 50.0);
    }
}
