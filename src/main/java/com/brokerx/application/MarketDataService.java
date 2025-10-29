package com.brokerx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class MarketDataService implements AutoCloseable {
    private final Random random = new Random();
    private final Map<String, MarketDataSnapshot> symbolState = new ConcurrentHashMap<>();

    public MarketDataSnapshot tickFor(String symbol, BigDecimal referencePrice) {
        return symbolState.compute(symbol, (sym, snapshot) -> advance(sym, snapshot, referencePrice));
    }

    public BigDecimal latestPrice(String symbol) {
        return symbolState.compute(symbol, (sym, snapshot) -> snapshot == null ? initial(sym) : snapshot).price();
    }

    private MarketDataSnapshot initial(String symbol) {
        BigDecimal base = BigDecimal.valueOf(100 + random.nextInt(200)).setScale(2, RoundingMode.HALF_UP);
        return new MarketDataSnapshot(symbol, base, Instant.now());
    }

    private MarketDataSnapshot advance(String symbol, MarketDataSnapshot snapshot, BigDecimal referencePrice) {
        if (snapshot == null) {
            BigDecimal base = referencePrice != null ? referencePrice.setScale(2, RoundingMode.HALF_UP) : null;
            if (base == null) {
                return initial(symbol);
            }
            return new MarketDataSnapshot(symbol, base, Instant.now());
        }
        double basisPoints = (random.nextDouble() - 0.5) * 0.02; // +/-1%
        BigDecimal factor = BigDecimal.valueOf(1 + basisPoints);
        BigDecimal updated = snapshot.price().multiply(factor).max(BigDecimal.valueOf(1)).setScale(2, RoundingMode.HALF_UP);
        return new MarketDataSnapshot(symbol, updated, Instant.now());
    }

    @Override
    public void close() {
        symbolState.clear();
    }

    public record MarketDataSnapshot(String symbol, BigDecimal price, Instant timestamp) { }
}
