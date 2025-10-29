package com.brokerx.microservices;

import com.brokerx.application.StockService;
import com.brokerx.interfaces.rest.MetricsHandler;
import com.brokerx.interfaces.rest.TokenService;
import com.brokerx.interfaces.rest.microservices.MarketDataHandler;
import com.brokerx.observability.StructuredLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class MarketDataMicroservice implements AutoCloseable {
    private static final StructuredLogger LOGGER = StructuredLogger.get(MarketDataMicroservice.class);

    private final HttpServer server;

    public MarketDataMicroservice(int port, StockService stockService, TokenService tokenService) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start MarketData microservice", ex);
        }
        this.server.createContext("/stocks", new MarketDataHandler(stockService, tokenService));
        this.server.createContext("/metrics", new MetricsHandler());
        this.server.createContext("/health", MarketDataMicroservice::health);
    }

    public void start() {
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        LOGGER.info("marketdata_microservice_started");
        System.out.println("MarketData microservice ready on http://localhost:" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void health(HttpExchange exchange) throws IOException {
        byte[] payload = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
