package com.brokerx.integration;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.brokerx.adapters.external.PaymentAdapterStub;
import com.brokerx.adapters.persistence.memory.InMemoryAccountAuditRepository;
import com.brokerx.adapters.persistence.memory.InMemoryAccountRepository;
import com.brokerx.adapters.persistence.memory.InMemoryTransactionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryWalletRepository;
import com.brokerx.adapters.persistence.memory.NoopTransactionManager;
import com.brokerx.application.AuthService;
import com.brokerx.application.WalletService;

public class SignupAndDepositFlowTest {

    @Test
    void shouldCreateAccountAndDeposit() {
        var accountRepo = new InMemoryAccountRepository();
        var walletRepo  = new InMemoryWalletRepository();
        var txRepo      = new InMemoryTransactionRepository();
        var auditRepo   = new InMemoryAccountAuditRepository();
        var payment     = new PaymentAdapterStub();

        var authService   = new AuthService(accountRepo, walletRepo, auditRepo);
        var walletService = new WalletService(walletRepo, txRepo, payment, new NoopTransactionManager());

        var signup = authService.register(new AuthService.SignupCommand(
                "test@brokerx.io",
                "+15145550000",
                "pass123",
                "Test User",
                "123 Demo Street",
                java.time.LocalDate.of(1992, 5, 20)
        ));
        var accountId = signup.accountId();
        authService.confirmAccount(accountId, signup.verificationCode());

        var tx = walletService.deposit(accountId, "KEY-1", 250.0);

        assertEquals(BigDecimal.valueOf(250.0), tx.getAmount());
        assertEquals("SETTLED", tx.getState());
    }
}
