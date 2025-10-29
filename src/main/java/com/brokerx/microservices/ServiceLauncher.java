package com.brokerx.microservices;

import com.brokerx.application.AuthService;
import com.brokerx.application.MarketDataService;
import com.brokerx.application.NotificationService;
import com.brokerx.application.OrderService;
import com.brokerx.application.StockService;
import com.brokerx.application.WalletService;
import com.brokerx.bootstrap.PersistenceProvider;
import com.brokerx.bootstrap.PersistenceProvider.PersistenceContext;
import com.brokerx.interfaces.rest.TokenService;

import java.time.Duration;

public final class ServiceLauncher {
    private ServiceLauncher() {
    }

    public static void main(String[] args) throws Exception {
        MicroserviceType type = args.length > 0
                ? MicroserviceType.valueOf(args[0].toUpperCase())
                : MicroserviceType.fromEnvironment();

        int port = portFromEnv();
        PersistenceContext persistence = PersistenceProvider.initialise();
        var notificationService = new NotificationService(200);
        var marketDataService = new MarketDataService();
        TokenService tokenService = requireToken() ? new TokenService(Duration.ofHours(4)) : null;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                marketDataService.close();
            } catch (Exception ignored) { }
            try {
                persistence.close();
            } catch (Exception ignored) { }
        }));

        try {
            switch (type) {
                case ORDERS -> startOrders(port, persistence, marketDataService, notificationService, tokenService);
                case PORTFOLIO -> startPortfolio(port, persistence, marketDataService, notificationService, tokenService);
                case MARKETDATA -> startMarketData(port, persistence, marketDataService, tokenService);
                case REPORTING -> startReporting(port, persistence, tokenService);
            }
        } catch (Exception ex) {
            persistence.close();
            marketDataService.close();
            throw ex;
        }
    }

    private static void startOrders(int port,
                                    PersistenceContext persistence,
                                    MarketDataService marketDataService,
                                    NotificationService notificationService,
                                    TokenService tokenService) {
        var authService = new AuthService(
                persistence.accountRepository(),
                persistence.walletRepository(),
                persistence.accountAuditRepository()
        );
        var walletService = new WalletService(
                persistence.walletRepository(),
                persistence.transactionRepository(),
                persistence.paymentAdapter(),
                persistence.transactionManager()
        );
        var orderService = new OrderService(
                authService,
                walletService,
                marketDataService,
                persistence.orderRepository(),
                persistence.stockRepository(),
                persistence.positionRepository(),
                persistence.orderAuditRepository(),
                notificationService,
                persistence.transactionManager()
        );
        new OrdersMicroservice(port, orderService, tokenService).start();
    }

    private static void startPortfolio(int port,
                                       PersistenceContext persistence,
                                       MarketDataService marketDataService,
                                       NotificationService notificationService,
                                       TokenService tokenService) {
        var authService = new AuthService(
                persistence.accountRepository(),
                persistence.walletRepository(),
                persistence.accountAuditRepository()
        );
        var walletService = new WalletService(
                persistence.walletRepository(),
                persistence.transactionRepository(),
                persistence.paymentAdapter(),
                persistence.transactionManager()
        );
        var orderService = new OrderService(
                authService,
                walletService,
                marketDataService,
                persistence.orderRepository(),
                persistence.stockRepository(),
                persistence.positionRepository(),
                persistence.orderAuditRepository(),
                notificationService,
                persistence.transactionManager()
        );
        var stockService = new StockService(persistence.stockRepository(), marketDataService, orderService);
        new PortfolioMicroservice(port, authService, walletService, stockService, tokenService).start();
    }

    private static void startMarketData(int port,
                                        PersistenceContext persistence,
                                        MarketDataService marketDataService,
                                        TokenService tokenService) {
        var stockService = new StockService(persistence.stockRepository(), marketDataService, null);
        new MarketDataMicroservice(port, stockService, tokenService).start();
    }

    private static void startReporting(int port,
                                       PersistenceContext persistence,
                                       TokenService tokenService) {
        new ReportingMicroservice(port, persistence.orderRepository(), tokenService).start();
    }

    private static boolean requireToken() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("BROKERX_REQUIRE_TOKEN", "false"));
    }

    private static int portFromEnv() {
        String raw = System.getenv().getOrDefault("BROKERX_HTTP_PORT", "").trim();
        if (raw.isEmpty()) {
            return 8090;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 8090;
        }
    }
}

