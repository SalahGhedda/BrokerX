package com.brokerx.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TimedCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    private final long ttlNanos;

    public TimedCache(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlNanos = ttl.toNanos();
    }

    public V getOrCompute(K key, Supplier<V> loader) {
        Objects.requireNonNull(loader, "loader");
        long now = System.nanoTime();
        CacheEntry<V> cached = store.get(key);
        if (cached != null && now - cached.createdAtNanos <= ttlNanos) {
            return cached.value;
        }
        V value = loader.get();
        store.put(key, new CacheEntry<>(value, now));
        return value;
    }

    public Optional<V> getIfPresent(K key) {
        CacheEntry<V> cached = store.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (System.nanoTime() - cached.createdAtNanos > ttlNanos) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(cached.value);
    }

    public void put(K key, V value) {
        store.put(key, new CacheEntry<>(value, System.nanoTime()));
    }

    public void invalidate(K key) {
        store.remove(key);
    }

    public void clear() {
        store.clear();
    }

    private record CacheEntry<V>(V value, long createdAtNanos) { }
}
