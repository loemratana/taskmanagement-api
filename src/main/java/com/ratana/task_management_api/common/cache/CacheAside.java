package com.ratana.task_management_api.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * One Caffeine cache used with the cache-aside pattern: the application, not the cache, talks to
 * the database.
 *
 * <pre>
 * read:  value = cache.get(key)
 *        if miss: value = load from DB; cache.put(key, value)
 * write: write to DB; then cache.invalidate(key)   (after the transaction commits)
 * </pre>
 *
 * <p><b>Why invalidate after commit:</b> invalidating inside the transaction would let another
 * request miss, read the still-old row, and cache it again before the write is visible.
 *
 * <p><b>Stale-put guard:</b> the classic cache-aside race is a slow read that loads the old value,
 * then a write commits and invalidates, then the slow read puts the old value back. Every
 * invalidation bumps {@link #generation}; a read only puts if no invalidation happened while it was
 * loading. (Coarse on purpose: any invalidation skips in-flight puts for every key, which only
 * costs an extra miss.)
 *
 * <p>Null values are not cached, so a missing row is looked up again next time.
 */
@Slf4j
public final class CacheAside<K, V> {

    private final String name;
    private final Cache<K, V> cache;
    private final AtomicLong generation = new AtomicLong();

    public CacheAside(String name, Cache<K, V> cache) {
        this.name = name;
        this.cache = cache;
    }

    /** Cache-aside read: cached value, or {@code loader}'s result (cached unless a write raced it). */
    public V get(K key, Supplier<V> loader) {
        V cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        long seen = generation.get();
        V loaded = loader.get();
        if (loaded != null && generation.get() == seen) {
            cache.put(key, loaded);
            // An invalidation landing between the check and the put: take back exactly what we put.
            if (generation.get() != seen) {
                cache.asMap().remove(key, loaded);
            }
        }
        return loaded;
    }

    /** Drops {@code key} once the current transaction commits (at once if there is none). */
    public void invalidate(K key) {
        afterCommit(() -> {
            generation.incrementAndGet();
            cache.invalidate(key);
            log.debug("Cache {} invalidated key {}", name, key);
        });
    }

    /** Drops every entry once the current transaction commits (at once if there is none). */
    public void invalidateAll() {
        afterCommit(() -> {
            generation.incrementAndGet();
            cache.invalidateAll();
            log.debug("Cache {} cleared", name);
        });
    }

    public String name() {
        return name;
    }

    Cache<K, V> nativeCache() {
        return cache;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
