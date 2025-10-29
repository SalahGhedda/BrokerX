package com.brokerx.interfaces.rest;

import com.brokerx.application.AuthService;
import com.brokerx.application.NotificationService;
import com.brokerx.application.OrderService;
import com.brokerx.application.StockService;
import com.brokerx.application.WalletService;
import com.sun.net.httpserver.HttpServer;

public final class ApiRouter {
    private ApiRouter() {
    }

    public static void register(HttpServer server,
                                AuthService authService,
                                WalletService walletService,
                                StockService stockService,
                                OrderService orderService,
                                TokenService tokenService,
                                NotificationService notificationService) {
        server.createContext("/api/v1/auth", new AuthHandler(authService, tokenService));
        server.createContext("/api/v1/accounts", new AccountsRootHandler(authService));
        server.createContext("/api/v1/accounts/", new AccountResourceHandler(authService, walletService, stockService, orderService, notificationService, tokenService));
        server.createContext("/api/v1/stocks", new StocksHandler(stockService));
        server.createContext("/api/v1/stocks/", new StocksHandler(stockService));
    }
}
