package com.brokerx.adapters.persistence.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.brokerx.domain.order.TradeOrder;
import com.brokerx.ports.OrderRepository;

public class InMemoryOrderRepository implements OrderRepository {
    private final Map<UUID, TradeOrder> ordersById = new ConcurrentHashMap<>();
    private final Map<String, TradeOrder> ordersByClientKey = new ConcurrentHashMap<>();

    @Override
    public void save(TradeOrder order) {
        ordersById.put(order.id(), order);
        if (order.clientOrderId() != null && !order.clientOrderId().isBlank()) {
            ordersByClientKey.put(key(order.accountId(), order.clientOrderId()), order);
        }
    }

    @Override
    public void update(TradeOrder order) {
        save(order);
    }

    @Override
    public Optional<TradeOrder> findById(UUID orderId) {
        return Optional.ofNullable(ordersById.get(orderId));
    }

    @Override
    public Optional<TradeOrder> findByClientOrderId(UUID accountId, String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ordersByClientKey.get(key(accountId, clientOrderId)));
    }

    @Override
    public List<TradeOrder> findPendingByStock(UUID stockId) {
        List<TradeOrder> result = new ArrayList<>();
        for (TradeOrder order : ordersById.values()) {
            if (stockId.equals(order.stockId()) && order.isPending()) {
                result.add(order);
            }
        }
        result.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
        return result;
    }

    @Override
    public List<TradeOrder> findByAccount(UUID accountId) {
        List<TradeOrder> result = new ArrayList<>();
        for (TradeOrder order : ordersById.values()) {
            if (accountId.equals(order.accountId())) {
                result.add(order);
            }
        }
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return result;
    }

    @Override
    public List<TradeOrder> findAll() {
        List<TradeOrder> result = new ArrayList<>(ordersById.values());
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return result;
    }

    private String key(UUID accountId, String clientOrderId) {
        return accountId + "::" + clientOrderId;
    }
}
