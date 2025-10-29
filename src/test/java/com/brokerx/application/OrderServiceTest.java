package com.brokerx.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.brokerx.adapters.external.PaymentAdapterStub;
import com.brokerx.adapters.persistence.memory.InMemoryAccountAuditRepository;
import com.brokerx.adapters.persistence.memory.InMemoryAccountRepository;
import com.brokerx.adapters.persistence.memory.InMemoryOrderAuditRepository;
import com.brokerx.adapters.persistence.memory.InMemoryOrderRepository;
import com.brokerx.adapters.persistence.memory.InMemoryPositionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryTransactionRepository;
import com.brokerx.adapters.persistence.memory.InMemoryWalletRepository;
import com.brokerx.adapters.persistence.memory.NoopTransactionManager;
import com.brokerx.adapters.persistence.memory.InMemoryStockRepository;
import com.brokerx.application.NotificationService;
import com.brokerx.application.OrderService.OrderCommand;
import com.brokerx.application.OrderService.OrderResult;
import com.brokerx.domain.order.OrderStatus;
import com.brokerx.domain.wallet.Wallet;
import com.brokerx.ports.AccountAuditRepository;
import com.brokerx.ports.AccountRepository;
import com.brokerx.ports.OrderRepository;
import com.brokerx.ports.PositionRepository;
import com.brokerx.ports.TransactionRepository;
import com.brokerx.ports.WalletRepository;
import com.brokerx.ports.TransactionManager;

class OrderServiceTest {
    private AuthService authService;
    private WalletService walletService;
    private OrderService orderService;
    private WalletRepository walletRepository;
    private PositionRepository positionRepository;
    private InMemoryOrderAuditRepository orderAuditRepository;
    private NotificationService notificationService;
    private InMemoryStockRepository stockRepository;

    @BeforeEach
    void setUp() {
        AccountRepository accountRepository = new InMemoryAccountRepository();
        AccountAuditRepository auditRepository = new InMemoryAccountAuditRepository();
        walletRepository = new InMemoryWalletRepository();
        TransactionRepository txRepository = new InMemoryTransactionRepository();
        OrderRepository orderRepository = new InMemoryOrderRepository();
        stockRepository = new InMemoryStockRepository();
        positionRepository = new InMemoryPositionRepository();
        orderAuditRepository = new InMemoryOrderAuditRepository();
        TransactionManager transactionManager = new NoopTransactionManager();
        notificationService = new NotificationService(20);

        authService = new AuthService(accountRepository, walletRepository, auditRepository);
        walletService = new WalletService(walletRepository, txRepository, new PaymentAdapterStub(), transactionManager);
        MarketDataService marketDataService = new MarketDataService();
        orderService = new OrderService(
                authService,
                walletService,
                marketDataService,
                orderRepository,
                stockRepository,
                positionRepository,
                orderAuditRepository,
                notificationService,
                transactionManager
        );
    }

    @Test
    void placeLimitOrderExecutedImmediatelyDebitsWalletAtMarketPrice() {
        var signup = authService.register(new AuthService.SignupCommand(
                "order@test.com",
                "+15145550123",
                "secret123",
                "Trader Test",
                "1 Way Street",
                java.time.LocalDate.of(1990, 1, 1)
        ));
        UUID accountId = signup.accountId();
        authService.confirmAccount(accountId, signup.verificationCode());
        walletService.deposit(accountId, "init-deposit", 5000.0);

        BigDecimal marketPrice = stockRepository.findBySymbol("AAPL").orElseThrow().getLastPrice();
        BigDecimal limitPrice = marketPrice.multiply(BigDecimal.valueOf(0.99)).setScale(2, RoundingMode.HALF_UP);
        OrderCommand command = new OrderCommand("AAPL", "BUY", "LIMIT", "10", limitPrice.toPlainString(), "client-1");
        Wallet walletBefore = walletRepository.findByOwnerId(accountId).orElseThrow();
        BigDecimal balanceBefore = walletBefore.getBalance();

        OrderResult result = orderService.placeOrder(accountId, command);

        assertEquals("AAPL", result.symbol());
        assertEquals(10, result.quantity());
        assertEquals(OrderStatus.COMPLETED, result.status());
        assertNotNull(result.executedPrice());
        BigDecimal expectedNotional = result.executedPrice()
                .multiply(BigDecimal.valueOf(result.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedNotional, result.notional());

        Wallet walletAfter = walletRepository.findByOwnerId(accountId).orElseThrow();
        BigDecimal expectedBalance = balanceBefore.subtract(expectedNotional);
        assertEquals(expectedBalance.doubleValue(), walletAfter.getBalance().doubleValue(), 0.01);

        var position = positionRepository.find(accountId, result.stockId()).orElseThrow();
        assertEquals(BigDecimal.valueOf(result.quantity()).setScale(2, RoundingMode.HALF_UP), position.quantity());
        assertEquals(result.executedPrice().setScale(2, RoundingMode.HALF_UP), position.averagePrice());
        assertTrue(orderAuditRepository.entries().stream()
                .anyMatch(entry -> entry.orderId().equals(result.orderId()) && entry.eventType().equals("ORDER_COMPLETED")));

        // idempotent replay with same clientOrderId returns same order
        OrderResult replay = orderService.placeOrder(accountId, command);
        assertEquals(result.orderId(), replay.orderId());
        Wallet walletAfterReplay = walletRepository.findByOwnerId(accountId).orElseThrow();
        assertEquals(walletAfter.getBalance().doubleValue(), walletAfterReplay.getBalance().doubleValue(), 0.01);

        var notifications = notificationService.list(accountId);
        assertEquals(1, notifications.size());
        assertEquals("ORDER_COMPLETED", notifications.get(0).category());
    }

    @Test
    void placeLimitOrderAboveMarketIsPendingAndKeepsFundsAvailable() {
        var signup = authService.register(new AuthService.SignupCommand(
                "pending@test.com",
                "+15145550125",
                "secret123",
                "Trader Pending",
                "3 Way Street",
                java.time.LocalDate.of(1992, 3, 3)
        ));
        UUID accountId = signup.accountId();
        authService.confirmAccount(accountId, signup.verificationCode());
        walletService.deposit(accountId, "init-deposit", 2000.0);

        Wallet walletBefore = walletRepository.findByOwnerId(accountId).orElseThrow();
        BigDecimal balanceBefore = walletBefore.getBalance();

        OrderCommand command = new OrderCommand("AAPL", "BUY", "LIMIT", "5", "250", "client-2");
        BigDecimal reserved = BigDecimal.valueOf(250).setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(5)).setScale(2, RoundingMode.HALF_UP);
        OrderResult result = orderService.placeOrder(accountId, command);

        assertEquals(OrderStatus.PENDING, result.status());
        assertEquals("AAPL", result.symbol());
        assertEquals(5, result.quantity());
        assertEquals(reserved.doubleValue(), result.notional().doubleValue(), 0.001);
        Wallet walletAfter = walletRepository.findByOwnerId(accountId).orElseThrow();
        assertEquals(balanceBefore.subtract(reserved).doubleValue(), walletAfter.getBalance().doubleValue(), 0.001);
        assertTrue(orderAuditRepository.entries().stream()
                .anyMatch(entry -> entry.orderId().equals(result.orderId()) && entry.eventType().equals("ORDER_PENDING")));

        var notifications = notificationService.list(accountId);
        assertEquals(1, notifications.size());
        assertEquals("ORDER_PENDING", notifications.get(0).category());
    }

    @Test
    void cancellingPendingOrderMarksCancelledAndLeavesBalanceUntouched() {
        var signup = authService.register(new AuthService.SignupCommand(
                "cancel@test.com",
                "+15145550126",
                "secret123",
                "Trader Cancel",
                "4 Way Street",
                java.time.LocalDate.of(1993, 4, 4)
        ));
        UUID accountId = signup.accountId();
        authService.confirmAccount(accountId, signup.verificationCode());
        walletService.deposit(accountId, "init-deposit", 1500.0);

        Wallet walletBefore = walletRepository.findByOwnerId(accountId).orElseThrow();
        BigDecimal balanceBefore = walletBefore.getBalance();

        BigDecimal marketPrice = stockRepository.findBySymbol("AAPL").orElseThrow().getLastPrice();
        BigDecimal limitPrice = marketPrice.multiply(BigDecimal.valueOf(1.10)).setScale(2, RoundingMode.HALF_UP);
        OrderCommand command = new OrderCommand("AAPL", "BUY", "LIMIT", "3", limitPrice.toPlainString(), "client-3");
        BigDecimal reserved = limitPrice.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);
        OrderResult pending = orderService.placeOrder(accountId, command);
        assertEquals(OrderStatus.PENDING, pending.status());
        Wallet walletAfterHold = walletRepository.findByOwnerId(accountId).orElseThrow();
        assertEquals(balanceBefore.subtract(reserved).doubleValue(), walletAfterHold.getBalance().doubleValue(), 0.001);

        OrderResult cancelled = orderService.cancelOrder(accountId, pending.orderId());
        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertEquals("Annule par le client", cancelled.failureReason());

        Wallet walletAfter = walletRepository.findByOwnerId(accountId).orElseThrow();
        assertEquals(balanceBefore.doubleValue(), walletAfter.getBalance().doubleValue(), 0.001);
        assertTrue(orderAuditRepository.entries().stream()
                .anyMatch(entry -> entry.orderId().equals(cancelled.orderId()) && entry.eventType().equals("ORDER_CANCELLED")));

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(accountId, pending.orderId()));

        var notifications = notificationService.list(accountId);
        assertEquals(2, notifications.size());
        assertEquals("ORDER_CANCELLED", notifications.get(0).category());
        assertEquals("ORDER_PENDING", notifications.get(1).category());
    }

    @Test
    void marketOrderWithInsufficientFundsIsRecordedAsFailed() {
        var signup = authService.register(new AuthService.SignupCommand(
                "failed@test.com",
                "+15145550127",
                "secret123",
                "Trader Fail",
                "5 Way Street",
                java.time.LocalDate.of(1994, 5, 5)
        ));
        UUID accountId = signup.accountId();
        authService.confirmAccount(accountId, signup.verificationCode());

        OrderCommand command = new OrderCommand("AAPL", "BUY", "MARKET", "100", null, "client-fail");
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(accountId, command));
        Wallet walletAfter = walletRepository.findByOwnerId(accountId).orElseThrow();
        assertEquals(0.0, walletAfter.getBalance().doubleValue(), 0.001);
        assertTrue(orderAuditRepository.entries().isEmpty());
        assertTrue(notificationService.list(accountId).isEmpty());
    }

    @Test
    void placingOrderOnInactiveAccountFails() {
        var signup = authService.register(new AuthService.SignupCommand(
                "inactive@test.com",
                "+15145550124",
                "secret123",
                "Trader Inactive",
                "2 Way Street",
                java.time.LocalDate.of(1991, 2, 2)
        ));
        UUID accountId = signup.accountId();
        assertThrows(IllegalStateException.class, () -> orderService.placeOrder(
                accountId,
                new OrderCommand("AAPL", "BUY", "MARKET", "1", null, null)
        ));
    }
}


