package com.brokerx.adapters.persistence.memory;

import com.brokerx.domain.position.Position;
import com.brokerx.ports.PositionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPositionRepository implements PositionRepository {
    private final Map<String, Position> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Position> find(UUID accountId, UUID stockId) {
        return Optional.ofNullable(store.get(key(accountId, stockId)));
    }

    @Override
    public void upsert(Position position) {
        store.put(key(position.accountId(), position.stockId()), position);
    }

    @Override
    public List<Position> listByAccount(UUID accountId) {
        List<Position> results = new ArrayList<>();
        for (Position position : store.values()) {
            if (position.accountId().equals(accountId)) {
                results.add(position);
            }
        }
        return results;
    }

    private String key(UUID accountId, UUID stockId) {
        return accountId + "::" + stockId;
    }
}
