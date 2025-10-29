package com.brokerx.bootstrap;

import com.brokerx.adapters.external.PaymentAdapterStub;
import com.brokerx.adapters.persistence.jdbc.AccountAuditRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.AccountRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.JdbcTransactionManager;
import com.brokerx.adapters.persistence.jdbc.OrderAuditRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.OrderRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.PersistenceException;
import com.brokerx.adapters.persistence.jdbc.PositionRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.StockRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.TransactionRepositoryJdbc;
import com.brokerx.adapters.persistence.jdbc.WalletRepositoryJdbc;
import com.brokerx.adapters.persistence.memory.InMemoryAccountAuditRepository;
import com.brokerx.adapters.persistence.memory.InMemoryAccountRepository;
import com.brokerx.adapters.persistence.memory.InMemoryOrderAuditRepository;
import com.brokerx.adapters.persistence.memory.InMemoryOrderRepository;
import com.brokerx.adapters.persistence.memory.InMemoryPositionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryStockRepository;
import com.brokerx.adapters.persistence.memory.InMemoryTransactionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryWalletRepository;
import com.brokerx.adapters.persistence.memory.NoopTransactionManager;
import com.brokerx.ports.AccountAuditRepository;
import com.brokerx.ports.AccountRepository;
import com.brokerx.ports.OrderAuditRepository;
import com.brokerx.ports.OrderRepository;
import com.brokerx.ports.PositionRepository;
import com.brokerx.ports.StockRepository;
import com.brokerx.ports.TransactionManager;
import com.brokerx.ports.TransactionRepository;
import com.brokerx.ports.WalletRepository;
import com.zaxxer.hikari.HikariDataSource;

public final class PersistenceProvider {
    private PersistenceProvider() {
    }

    public static PersistenceContext initialise() {
        boolean useInMemory = Boolean.parseBoolean(System.getenv().getOrDefault("BROKERX_USE_IN_MEMORY", "false"));
        if (useInMemory) {
            System.out.println("BrokerX running with in-memory repositories.");
            return createInMemoryContext();
        }

        try {
            HikariDataSource dataSource = DataSourceFactory.createFromEnvironment();
            new DatabaseMigrator(dataSource).migrate();
            System.out.println("BrokerX connected to database: " + dataSource.getJdbcUrl());
            var transactionManager = new JdbcTransactionManager(dataSource);
            return new PersistenceContext(
                    new AccountRepositoryJdbc(dataSource),
                    new WalletRepositoryJdbc(dataSource),
                    new TransactionRepositoryJdbc(dataSource),
                    new StockRepositoryJdbc(dataSource),
                    new AccountAuditRepositoryJdbc(dataSource),
                    new OrderRepositoryJdbc(dataSource),
                    new PositionRepositoryJdbc(dataSource),
                    new OrderAuditRepositoryJdbc(dataSource),
                    transactionManager,
                    dataSource
            );
        } catch (PersistenceException ex) {
            System.err.println("Database bootstrap failed, falling back to in-memory stores: " + ex.getMessage());
            return createInMemoryContext();
        } catch (RuntimeException ex) {
            System.err.println("Unexpected bootstrap error, using in-memory repositories: " + ex.getMessage());
            return createInMemoryContext();
        }
    }

    public static PersistenceContext createInMemoryContext() {
        return new PersistenceContext(
                new InMemoryAccountRepository(),
                new InMemoryWalletRepository(),
                new InMemoryTransactionRepository(),
                new InMemoryStockRepository(),
                new InMemoryAccountAuditRepository(),
                new InMemoryOrderRepository(),
                new InMemoryPositionRepository(),
                new InMemoryOrderAuditRepository(),
                new NoopTransactionManager(),
                null
        );
    }

    public record PersistenceContext(
            AccountRepository accountRepository,
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            StockRepository stockRepository,
            AccountAuditRepository accountAuditRepository,
            OrderRepository orderRepository,
            PositionRepository positionRepository,
            OrderAuditRepository orderAuditRepository,
            TransactionManager transactionManager,
            AutoCloseable cleanup
    ) implements AutoCloseable {

        public PaymentAdapterStub paymentAdapter() {
            return new PaymentAdapterStub();
        }

        @Override
        public void close() {
            if (cleanup == null) {
                return;
            }
            try {
                cleanup.close();
            } catch (Exception ex) {
                System.err.println("Failed to close persistence resources: " + ex.getMessage());
            }
        }
    }
}
