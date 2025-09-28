package com.brokerx.integration;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.brokerx.adapters.external.PaymentAdapterMock;
import com.brokerx.adapters.persistence.memory.InMemoryAccountRepository;
import com.brokerx.adapters.persistence.memory.InMemoryTransactionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryWalletRepository;
import com.brokerx.application.AuthService;
import com.brokerx.application.WalletService;

public class SignupAndDepositFlowTest {

    @Test
    void shouldCreateAccountAndDeposit() {
        var accountRepo = new InMemoryAccountRepository();
        var walletRepo  = new InMemoryWalletRepository();
        var txRepo      = new InMemoryTransactionRepository();
        var payment     = new PaymentAdapterMock();

        var authService   = new AuthService(accountRepo, walletRepo);
        var walletService = new WalletService(walletRepo, txRepo, payment);

        var accountId = authService.createAccount("test@brokerx.io", "pass123");
        authService.confirmAccount(accountId);

        var tx = walletService.deposit(accountId, "KEY-1", 250.0);

        assertEquals(BigDecimal.valueOf(250.0), tx.getAmount());
        assertEquals("SETTLED", tx.getState());
    }
}
