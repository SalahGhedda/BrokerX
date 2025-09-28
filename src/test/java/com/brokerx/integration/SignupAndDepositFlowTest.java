package com.brokerx.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.brokerx.application.*;
import com.brokerx.adapters.persistence.memory.*;
import com.brokerx.adapters.external.*;

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

        assertEquals(250.0, tx.getAmount());
        assertEquals("SETTLED", tx.getState());
    }
}
