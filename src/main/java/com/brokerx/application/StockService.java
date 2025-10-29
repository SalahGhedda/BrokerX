package com.brokerx.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.brokerx.application.MarketDataService.MarketDataSnapshot;
import com.brokerx.cache.TimedCache;
import com.brokerx.domain.stock.Stock;
import com.brokerx.observability.StructuredLogger;
import com.brokerx.ports.StockRepository;

public class StockService {
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(1);
    private static final String ALL_CACHE_KEY = "ALL";

    private final StockRepository stockRepository;
    private final MarketDataService marketDataService;
    private final OrderService orderService;
    private final TimedCache<String, List<Quote>> allQuotesCache;
    private final TimedCache<UUID, List<Quote>> followedQuotesCache;
    private final TimedCache<UUID, Quote> quoteCache;
    private final StructuredLogger logger;

    public StockService(StockRepository stockRepository, MarketDataService marketDataService, OrderService orderService) {
        this(stockRepository, marketDataService, orderService, DEFAULT_CACHE_TTL);
    }

    public StockService(StockRepository stockRepository,
                        MarketDataService marketDataService,
                        OrderService orderService,
                        Duration cacheTtl) {
        this.stockRepository = stockRepository;
        this.marketDataService = marketDataService;
        this.orderService = orderService;
        this.logger = StructuredLogger.get(StockService.class);
        Duration ttl = cacheTtl != null ? cacheTtl : DEFAULT_CACHE_TTL;
        this.allQuotesCache = new TimedCache<>(ttl);
        this.followedQuotesCache = new TimedCache<>(ttl);
        this.quoteCache = new TimedCache<>(ttl);
    }

    public List<Quote> listAll() {
        return allQuotesCache.getOrCompute(ALL_CACHE_KEY, this::loadAllQuotes);
    }

    public List<Quote> listFollowed(UUID accountId) {
        return followedQuotesCache.getOrCompute(accountId, () -> loadFollowedQuotes(accountId));
    }

    public Quote getQuote(UUID stockId) {
        return quoteCache.getOrCompute(stockId, () -> loadQuote(stockId));
    }

    public Stock requireBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Symbole inconnu: " + symbol));
    }

    public void follow(UUID accountId, UUID stockId) {
        assertStockExists(stockId);
        stockRepository.follow(accountId, stockId);
        followedQuotesCache.invalidate(accountId);
        logger.info("stock_follow", Map.of(
                "accountId", accountId.toString(),
                "stockId", stockId.toString()
        ));
    }

    public void unfollow(UUID accountId, UUID stockId) {
        stockRepository.unfollow(accountId, stockId);
        followedQuotesCache.invalidate(accountId);
        logger.info("stock_unfollow", Map.of(
                "accountId", accountId.toString(),
                "stockId", stockId.toString()
        ));
    }

    private List<Quote> loadAllQuotes() {
        List<Quote> quotes = stockRepository.findAll().stream()
                .map(this::refreshQuote)
                .toList();
        logger.info("stocks_cache_refresh", Map.of(
                "scope", "ALL",
                "count", quotes.size()
        ));
        return quotes;
    }

    private List<Quote> loadFollowedQuotes(UUID accountId) {
        List<Quote> quotes = stockRepository.findFollowedByAccount(accountId).stream()
                .map(this::refreshQuote)
                .toList();
        logger.info("stocks_cache_refresh", Map.of(
                "scope", "FOLLOWED",
                "accountId", accountId.toString(),
                "count", quotes.size()
        ));
        return quotes;
    }

    private Quote loadQuote(UUID stockId) {
        var stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock introuvable"));
        return refreshQuote(stock);
    }

    private void assertStockExists(UUID stockId) {
        if (stockRepository.findById(stockId).isEmpty()) {
            throw new IllegalArgumentException("Stock introuvable");
        }
    }

    private Quote refreshQuote(Stock stock) {
        MarketDataSnapshot snapshot = marketDataService.tickFor(
                stock.getSymbol(),
                stock.getLastPrice()
        );
        stock.updatePrice(snapshot.price(), snapshot.timestamp());
        stockRepository.updatePrice(stock.getId(), stock);
        if (orderService != null) {
            orderService.onMarketTick(stock.getId(), stock.getSymbol(), snapshot.price(), snapshot.timestamp());
        }
        Quote quote = new Quote(
                stock.getId(),
                stock.getSymbol(),
                stock.getName(),
                stock.getDescription(),
                snapshot.price(),
                snapshot.timestamp()
        );
        quoteCache.put(quote.id(), quote);
        return quote;
    }

    public record Quote(
            UUID id,
            String symbol,
            String name,
            String description,
            BigDecimal price,
            Instant updatedAt
    ) { }
}
