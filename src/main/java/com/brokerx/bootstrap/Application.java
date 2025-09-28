package com.brokerx.bootstrap;

import com.brokerx.application.*;
import com.brokerx.adapters.persistence.memory.*;
import com.brokerx.adapters.external.*;

public class Application {
    public static void main(String[] args) {
        var accountRepo = new InMemoryAccountRepository();
        var walletRepo  = new InMemoryWalletRepository();
        var txRepo      = new InMemoryTransactionRepository();
        var payment     = new PaymentAdapterMock();

        var authService   = new AuthService(accountRepo, walletRepo);
        var walletService = new WalletService(walletRepo, txRepo, payment);

        // Démonstration simple
        var accountId = authService.createAccount("demo@brokerx.io", "pass123");
        authService.confirmAccount(accountId);
        walletService.deposit(accountId, "DEMO_KEY_1", 100.0);

        System.out.println("Prototype exécuté avec succès !");
    }
}