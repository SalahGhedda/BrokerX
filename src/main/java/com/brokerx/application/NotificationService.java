package com.brokerx.application;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {
    private final Map<UUID, Deque<Notification>> store = new ConcurrentHashMap<>();
    private final int capacity;

    public NotificationService(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public Notification publish(UUID accountId,
                                String category,
                                String message,
                                String referenceId,
                                String payload) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        Notification notification = new Notification(
                UUID.randomUUID(),
                accountId,
                category,
                message,
                referenceId,
                Instant.now(),
                payload
        );
        Deque<Notification> deque = store.computeIfAbsent(accountId, id -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addFirst(notification);
            while (deque.size() > capacity) {
                deque.removeLast();
            }
        }
        return notification;
    }

    public List<Notification> list(UUID accountId) {
        Deque<Notification> deque = store.get(accountId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }

    public void clear(UUID accountId) {
        Deque<Notification> deque = store.get(accountId);
        if (deque == null) {
            return;
        }
        synchronized (deque) {
            deque.clear();
        }
    }

    public record Notification(
            UUID id,
            UUID accountId,
            String category,
            String message,
            String referenceId,
            Instant createdAt,
            String payload
    ) { }
}
