package com.brokerx.ports;

import com.brokerx.domain.position.Position;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository {
    Optional<Position> find(UUID accountId, UUID stockId);

    void upsert(Position position);

    List<Position> listByAccount(UUID accountId);
}
