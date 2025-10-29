package com.brokerx.domain.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Stock {
    private final UUID id;
    private final String symbol;
    private final String name;
    private final String description;
    private BigDecimal lastPrice;
    private Instant updatedAt;

    public Stock(UUID id, String symbol, String name, String description, BigDecimal lastPrice, Instant updatedAt) {
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.description = description;
        this.lastPrice = lastPrice;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updatePrice(BigDecimal price, Instant at) {
        this.lastPrice = price;
        this.updatedAt = at;
    }
}
