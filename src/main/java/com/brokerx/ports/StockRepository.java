package com.brokerx.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.domain.stock.Stock;

public interface StockRepository {
    List<Stock> findAll();
    Optional<Stock> findById(UUID stockId);
    Optional<Stock> findBySymbol(String symbol);
    void updatePrice(UUID stockId, Stock stock);
    List<Stock> findFollowedByAccount(UUID accountId);
    void follow(UUID accountId, UUID stockId);
    void unfollow(UUID accountId, UUID stockId);
}
