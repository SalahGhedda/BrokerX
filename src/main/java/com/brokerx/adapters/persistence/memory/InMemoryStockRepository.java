package com.brokerx.adapters.persistence.memory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.brokerx.domain.stock.Stock;
import com.brokerx.ports.StockRepository;

public class InMemoryStockRepository implements StockRepository {
    private final Map<UUID, Stock> stocks = new ConcurrentHashMap<>();
    private final Map<String, UUID> stockBySymbol = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> followsByAccount = new ConcurrentHashMap<>();

    public InMemoryStockRepository() {
        seed();
    }

    private void seed() {
        add(new Stock(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "AAPL",
                "Apple Inc.",
                "Technologie grand public et services numeriques.",
                BigDecimal.valueOf(185.32),
                Instant.now()
        ));
        add(new Stock(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "GOOGL",
                "Alphabet Inc.",
                "Maison mere de Google, specialisee dans la recherche et le cloud.",
                BigDecimal.valueOf(142.11),
                Instant.now()
        ));
        add(new Stock(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "TSLA",
                "Tesla, Inc.",
                "Constructeur de vehicules electriques et solutions energetiques.",
                BigDecimal.valueOf(209.45),
                Instant.now()
        ));
        add(new Stock(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "AMZN",
                "Amazon.com, Inc.",
                "Plateforme e-commerce et services cloud (AWS).",
                BigDecimal.valueOf(129.63),
                Instant.now()
        ));
        add(new Stock(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "SHOP",
                "Shopify Inc.",
                "Solutions SaaS pour commercants et vente en ligne (Canada).",
                BigDecimal.valueOf(68.27),
                Instant.now()
        ));
    }

    private void add(Stock stock) {
        stocks.put(stock.getId(), stock);
        stockBySymbol.put(stock.getSymbol(), stock.getId());
    }

    @Override
    public List<Stock> findAll() {
        return new ArrayList<>(stocks.values());
    }

    @Override
    public Optional<Stock> findById(UUID stockId) {
        return Optional.ofNullable(stocks.get(stockId));
    }

    @Override
    public Optional<Stock> findBySymbol(String symbol) {
        var id = stockBySymbol.get(symbol);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(stocks.get(id));
    }

    @Override
    public void updatePrice(UUID stockId, Stock updated) {
        stocks.put(stockId, updated);
        stockBySymbol.put(updated.getSymbol(), stockId);
    }

    @Override
    public List<Stock> findFollowedByAccount(UUID accountId) {
        return followsByAccount.getOrDefault(accountId, List.of()).stream()
                .map(stocks::get)
                .filter(stock -> stock != null)
                .toList();
    }

    @Override
    public void follow(UUID accountId, UUID stockId) {
        followsByAccount.computeIfAbsent(accountId, id -> new ArrayList<>());
        var list = followsByAccount.get(accountId);
        if (!list.contains(stockId)) {
            list.add(stockId);
        }
    }

    @Override
    public void unfollow(UUID accountId, UUID stockId) {
        var list = followsByAccount.get(accountId);
        if (list != null) {
            list.remove(stockId);
        }
    }
}
