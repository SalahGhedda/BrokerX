package com.brokerx.bootstrap;

import java.time.Duration;

import com.brokerx.adapters.external.PaymentAdapterStub;
import com.brokerx.application.AuthService;
import com.brokerx.application.MarketDataService;
import com.brokerx.application.NotificationService;
import com.brokerx.application.OrderService;
import com.brokerx.application.StockService;
import com.brokerx.application.WalletService;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.ports.OrderRepository;
import com.brokerx.ports.StockRepository;

public final class Application {
    private Application() {
    }

    public static void main(String[] args) {
        var persistence = PersistenceProvider.initialise();
        var payment = new PaymentAdapterStub();
        var marketDataService = new MarketDataService();
        var notificationService = new NotificationService(200);
        var tokenService = new TokenService(Duration.ofHours(4));
        StockRepository stockRepository = persistence.stockRepository();
        OrderRepository orderRepository = persistence.orderRepository();

        var authService = new AuthService(
                persistence.accountRepository(),
                persistence.walletRepository(),
                persistence.accountAuditRepository()
        );
        var walletService = new WalletService(
                persistence.walletRepository(),
                persistence.transactionRepository(),
                payment,
                persistence.transactionManager()
        );
        var orderService = new OrderService(
                authService,
                walletService,
                marketDataService,
                orderRepository,
                stockRepository,
                persistence.positionRepository(),
                persistence.orderAuditRepository(),
                notificationService,
                persistence.transactionManager()
        );
        var stockService = new StockService(stockRepository, marketDataService, orderService);

        int port = httpPortFromEnv();
        var uiServer = new UiHttpServer(
                port,
                authService,
                walletService,
                stockService,
                orderService,
                tokenService,
                notificationService
        );
        uiServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            uiServer.close();
            persistence.close();
            marketDataService.close();
        }));

        System.out.println("BrokerX ready on http://localhost:" + port);
    }

    private static int httpPortFromEnv() {
        var portValue = System.getenv("BROKERX_HTTP_PORT");
        if (portValue == null || portValue.isBlank()) {
            return 8080;
        }
        try {
            return Integer.parseInt(portValue);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid BROKERX_HTTP_PORT value, using default 8080");
            return 8080;
        }
    }
}
