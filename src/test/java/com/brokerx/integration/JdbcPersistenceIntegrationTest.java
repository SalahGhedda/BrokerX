package com.brokerx.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.brokerx.adapters.external.PaymentAdapterStub;
import com.brokerx.adapters.persistence.jdbc.AccountAuditRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.AccountRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.TransactionRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.JdbcTransactionManager;
import com.brokerx.adapters.persistence.jdbc.WalletRepositoryJdbc;
import com.brokerx.application.AuthService;
import com.brokerx.application.WalletService;
import com.brokerx.bootstrap.DatabaseMigrator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class JdbcPersistenceIntegrationTest {
    private static HikariDataSource dataSource;
    private static AuthService authService;
    private static WalletService walletService;
    private static TransactionRepositoryJdbc transactionRepository;
    private static JdbcTransactionManager transactionManager;

    @BeforeAll
    static void setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:brokerx;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        new DatabaseMigrator(dataSource).migrate();

        var accountRepository = new AccountRepositoryJdbc(dataSource);
        var walletRepository = new WalletRepositoryJdbc(dataSource);
        transactionRepository = new TransactionRepositoryJdbc(dataSource);
        var auditRepository = new AccountAuditRepositoryJdbc(dataSource);
        transactionManager = new JdbcTransactionManager(dataSource);
        authService = new AuthService(accountRepository, walletRepository, auditRepository);
        walletService = new WalletService(walletRepository, transactionRepository, new PaymentAdapterStub(), transactionManager);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void shouldPersistAccountWalletAndTransaction() {
        var signup = authService.register(new AuthService.SignupCommand(
                "jdbc-user@brokerx.io",
                "+15145550999",
                "superSecret",
                "Jdbc User",
                "123 Integration Ave",
                java.time.LocalDate.of(1990, 3, 15)
        ));
        var accountId = signup.accountId();
        authService.confirmAccount(accountId, signup.verificationCode());

        walletService.deposit(accountId, "jdbc-test-1", 150.5);

        var walletOpt = walletService.findWallet(accountId);
        assertTrue(walletOpt.isPresent(), "Wallet should be persisted");
        assertEquals(0, walletOpt.get().getBalance().compareTo(new BigDecimal("150.5")));

        var txOpt = transactionRepository.findByIdempotencyKey("jdbc-test-1");
        assertTrue(txOpt.isPresent(), "Transaction should be persisted");
        assertEquals("SETTLED", txOpt.get().getState());
    }
}
