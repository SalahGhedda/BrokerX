package com.brokerx.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.brokerx.domain.order.TradeOrder;

public interface OrderRepository {
    void save(TradeOrder order);
    void update(TradeOrder order);
    Optional<TradeOrder> findById(UUID orderId);
    Optional<TradeOrder> findByClientOrderId(UUID accountId, String clientOrderId);
    List<TradeOrder> findPendingByStock(UUID stockId);
    List<TradeOrder> findByAccount(UUID accountId);
    List<TradeOrder> findAll();
}
